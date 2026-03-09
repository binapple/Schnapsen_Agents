package is_mcts_agent;

import at.ac.tuwien.ifs.sge.agent.AbstractGameAgent;
import at.ac.tuwien.ifs.sge.agent.GameAgent;
import at.ac.tuwien.ifs.sge.engine.Logger;
import at.ac.tuwien.ifs.sge.util.Util;
import game.Schnapsen;
import game.action.SchnapsenAction;
import game.board.PlayingCard;
import game.board.SchnapsenBoard;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class Is_Mcts_Agent_Schnapsen extends AbstractGameAgent<Schnapsen, SchnapsenAction> implements GameAgent<Schnapsen, SchnapsenAction> {

    private final double MCTS_EXPLORATION = Math.sqrt(2);

    private double oldUtilityPlayer0;
    private double oldUtilityPlayer1;

    //used for Tree re-use
    private Is_Mcts_Node_Schnapsen currentRootNode;

    public Is_Mcts_Agent_Schnapsen(Logger log) {
        super(log);
    }

    public Is_Mcts_Agent_Schnapsen() {
        super();
    }

    @Override
    public SchnapsenAction computeNextAction(Schnapsen schnapsen, long l, TimeUnit timeUnit) {

        SchnapsenBoard board = schnapsen.getBoard();
        Set<SchnapsenAction> availableActions = schnapsen.getPossibleActions();
        oldUtilityPlayer0 = schnapsen.getUtilityValue(0);
        oldUtilityPlayer1 = schnapsen.getUtilityValue(1);

        //single choice actions can be returned immediately
        if(availableActions.size() == 1){
            return availableActions.iterator().next();
        }

        setTimers(l,  timeUnit);

        // Detect if this is the start of a completely new Round
        boolean isNewRound = board.getPlayer0Score() == 0 && board.getPlayer1Score() == 0
                && board.getPlayer0Tricks().isEmpty() && board.getPlayer1Tricks().isEmpty();

        //first round check
        if(this.currentRootNode == null || isNewRound) {
            this.currentRootNode = new Is_Mcts_Node_Schnapsen(null, null);
        } else {
            //if it was not the first round we re-use the tree but have to change the root
            SchnapsenAction lastAction = schnapsen.getPreviousAction();
            if(lastAction  != null) {
                Is_Mcts_Node_Schnapsen nextRootNode = this.currentRootNode.findChildWithAction(lastAction);
                if(nextRootNode != null){
                    log._debugf("Reusing tree! Found opponent action: %s. Starting with %d prior visits.",
                            lastAction.toString(), nextRootNode.getVisitations());
                    this.currentRootNode = nextRootNode;
                    this.currentRootNode.setParentNode(null); // Cut off the parent (this is our new root)
                } else {
                    //if the action has not yet been simulated in the tree we start with an empty tree
                    log._debugf("Tree reuse failed. Opponent action '%s' was never simulated. Starting fresh.", lastAction.toString());
                    this.currentRootNode = new Is_Mcts_Node_Schnapsen(null, null);
                }
            }
        }

        List<PlayingCard> deckOfCards = generateFullDeck(board.getTrumpCard().getSuit());


        //keeping track of iterations -> how many simulations could be run
        int iterations = 0;
        //starting the IS-MCTS Algorithm -> it will run as long as possible
        while(!shouldStopComputation())
        {
            //Create a random determinization of the available board for each iteration
            SchnapsenBoard generatedBoard = generateMissingInformation(board,  deckOfCards);
            Schnapsen generatedSchnapsen = new Schnapsen(generatedBoard);

           // int maxDepth = calculateMaxDepth(generatedSchnapsen);
            //Track Integer over all methods
           // int[] depth = new int[]{0};

            Is_Mcts_Node_Schnapsen expandedNode = selectAndExpand(this.currentRootNode, generatedSchnapsen); //, maxDepth, depth);
            //int depthTillRoundEnd = maxDepth - depth[0];
            double simulationScore = simulateNode(generatedSchnapsen); // depthTillRoundEnd);
            backPropagateNode(expandedNode, simulationScore);
            iterations++;
        }

        log.debugf("IS-MCTS completed %d iterations", iterations);

        //finding out which action was the best and returning it
        SchnapsenAction bestAction = null;
        if(!this.currentRootNode.getChildNodes().isEmpty())
        {
            int maxVisits = -1;

            log.debug("--- Action Statistics ---");

            for (Is_Mcts_Node_Schnapsen child : this.currentRootNode.getChildNodes()) {
                // win rate calculation for logging
                double winRate = (child.getVisitations() > 0) ? (child.getScore() / child.getVisitations()) : 0.0;
                if(availableActions.contains(child.getParentAction())) {
                    // %-20s pads the action string to 20 chars for a clean table look
                    log._debugf("Action: %-20s | Visits: %6d | WinRate: %5.2f%% | Avail: %d",
                            child.getParentAction().toString(),
                            child.getVisitations(),
                            winRate * 100.0,
                            child.getAvailabilityCount());

                    // Find the most visited node
                    if (child.getVisitations() > maxVisits) {
                        maxVisits = child.getVisitations();
                        bestAction = child.getParentAction();
                    }
                } else {
                    log._debugf("Invalid action ignored: %s (Visits: %d)", child.getParentAction().toString(), child.getVisitations());
                }
            }
            log.debug("-----------------------------------");
        }

        //fallback if MCTS did not find an action
        if(bestAction == null)
        {
            bestAction = availableActions.iterator().next();
        }

        //advance our game tree based on our best action
        Is_Mcts_Node_Schnapsen bestMoveNode = this.currentRootNode.findChildWithAction(bestAction);
         if(bestMoveNode != null)
        {
            double expectedWinRate = (bestMoveNode.getVisitations() > 0) ? (bestMoveNode.getScore() / bestMoveNode.getVisitations()) : 0.0;
            log._debugf("--> CHOSEN ACTION: %s (Expected Win Rate: %.2f%%)", bestAction.toString(), expectedWinRate * 100.0);
            this.currentRootNode = bestMoveNode;
            this.currentRootNode.setParentNode(null); // reset the parent to create a new root
        }
        else
        {
            //we have to start again because there have not yet been simulations for this action
            this.currentRootNode = new Is_Mcts_Node_Schnapsen(null, null);
        }

        return bestAction;
    }

    private void backPropagateNode(Is_Mcts_Node_Schnapsen expandedNode, double simulationScore) {
        Is_Mcts_Node_Schnapsen propagationNode = expandedNode;
        while (propagationNode != null) {
            propagationNode.incrementVisistations();
            propagationNode.addScore(simulationScore);
            propagationNode = propagationNode.getParentNode();
        }
    }

    private double simulateNode(Schnapsen schnapsen) { //,  int  maxDepth) {

        //randomly play actions
        int simulationDepth = 0;
        while(!shouldStopComputation() && !schnapsen.isGameOver() && !this.isRoundOver(schnapsen)){ //&& simulationDepth < maxDepth){
            Set<SchnapsenAction> possibleActions = schnapsen.getPossibleActions();
            SchnapsenAction action = Util.selectRandom(possibleActions);
            schnapsen = (Schnapsen) schnapsen.doAction(action);
            simulationDepth++;
        }

        return simulationScore(schnapsen);
    }

    private double simulationScore(Schnapsen currentGame) {
        SchnapsenBoard board = currentGame.getBoard();
        if(currentGame.isGameOver()){
            if(playerId == 0)
            {
                if(board.getPlayer0BummerlAmount() < board.getBummerlMax())
                {
                    return 1.0;
                } else {
                    return 0.0;
                }
            }
            else {
                if(board.getPlayer1BummerlAmount() < board.getBummerlMax())
                {
                    return 1.0;
                } else {
                    return 0.0;
                }
            }
        } else if(!this.isRoundOver(currentGame))
        {
            double myUtilValue = currentGame.getUtilityValue(playerId) % 1.0;
            double theirUtilValue = currentGame.getUtilityValue(1-playerId) % 1.0;
            double utilValue = (myUtilValue - theirUtilValue + 1.0) / 2.0;
            return utilValue;
        } else {
            if (playerId == 0) {
                long newUtilValuePlayer0 = (long) currentGame.getUtilityValue(0);
                if (newUtilValuePlayer0 > oldUtilityPlayer0) {
                    return 1.0;
                } else {
                    return 0.0;
                }
            } else {
                long newUtilValuePlayer1 = (long) currentGame.getUtilityValue(1);
                if (newUtilValuePlayer1 > oldUtilityPlayer1) {
                    return 1.0;
                } else {
                    return 0.0;
                }
            }
        }
    }

    private Is_Mcts_Node_Schnapsen selectAndExpand(Is_Mcts_Node_Schnapsen rootNode, Schnapsen schnapsen) { //, int maxDepth, int[] depth) {
        Is_Mcts_Node_Schnapsen selectedNode = rootNode;
        SchnapsenBoard board = schnapsen.getBoard();

        if(schnapsen.isGameOver() || this.isRoundOver(schnapsen))
        {
            return selectedNode;
        }
        while(!schnapsen.isGameOver() && !this.isRoundOver(schnapsen) ) { //&&depth[0] < maxDepth && !shouldStopComputation()) {
            //Check for possible Actions in this version of Schnapsen, track Actions that have not been tried
            // and ones which are already part of the tree
            Set<SchnapsenAction> possibleActions = schnapsen.getPossibleActions();

           //increment availability counts
            for(Is_Mcts_Node_Schnapsen child : selectedNode.getChildNodes())
            {
                if (possibleActions.contains(child.getParentAction())) {
                    child.incrementAvailabilityCount();
                }
            }


            List<SchnapsenAction> notTriedActions = new ArrayList<>();
            List<Is_Mcts_Node_Schnapsen> childrenWithAction = new ArrayList<>();

            for (SchnapsenAction action : possibleActions) {
                Is_Mcts_Node_Schnapsen possibleChild = selectedNode.findChildWithAction(action);
                if (possibleChild == null) {
                    notTriedActions.add(action);
                } else {
                    childrenWithAction.add(possibleChild);
                }
            }

            //expand not yet tried action by randomly choosing one
            if(!notTriedActions.isEmpty()) {
                SchnapsenAction chosenAction = Util.selectRandom(notTriedActions);
                Is_Mcts_Node_Schnapsen expandedNode = new  Is_Mcts_Node_Schnapsen(chosenAction, selectedNode);
                selectedNode.addChild(expandedNode);

                chosenAction.doAction(board);
                //depth[0]++;
                return expandedNode;

            }

            //Selection if there was no expansion
            boolean opponentAction = schnapsen.getCurrentPlayer() != this.playerId;
            double bestUCT = Double.MIN_VALUE;
            for (Is_Mcts_Node_Schnapsen child : childrenWithAction) {
                double currentUCT = getUCT(child, opponentAction);
                if (currentUCT > bestUCT) {
                    selectedNode = child;
                    bestUCT = currentUCT;
                }
            }
            selectedNode.getParentAction().doAction(board);
            //depth[0]++;

        }
        return selectedNode;
    }

    private double getUCT(Is_Mcts_Node_Schnapsen child, boolean opponentAction) {
        //unvisited children should be prioritized
        if(child.getVisitations() < 1)
        {
            return Double.POSITIVE_INFINITY;
        }
        double exploitationPart = child.getScore() / child.getVisitations();

        if(opponentAction) {
            exploitationPart = 1.0 - exploitationPart;
        }

        double explorationPart = MCTS_EXPLORATION * Math.sqrt(Math.log(child.getAvailabilityCount()) / (child.getVisitations()));

        return  exploitationPart + explorationPart;
    }


    /**
     * This method takes all available information of the given board and randomizes the unknown cards into the drafting pile and opposing players hand
     * @param board the actual game board with hidden information
     * @return a new deep copied board with the given information and randomized cards for all unknown cards in the pile and opposing players hand
     */
    private SchnapsenBoard generateMissingInformation(SchnapsenBoard board, List<PlayingCard> cachedDeck) {
        List<PlayingCard> unknownCards = new ArrayList<>(cachedDeck);
        //Getting the players hand and all the marriage cards declared by the other player
        List<PlayingCard> playerCards = new ArrayList<>();
        List<PlayingCard> marriageCards = new ArrayList<>();
        List<PlayingCard> otherPlayersCards = new ArrayList<>();
        if(playerId == 0)
        {
            playerCards = board.getPlayer0Cards();
            marriageCards = board.getPlayer1Marriages();
        } else {
            playerCards = board.getPlayer1Cards();
            marriageCards = board.getPlayer0Marriages();
        }

        //Getting the trumpCard and the leading card
        PlayingCard trumpCard = board.getTrumpCard();
        PlayingCard leadingCard = board.getLeadingCard();

        //Checking if there was an exchange of trumps
        PlayingCard oldTrumpCard = board.getOldTrumpCard();

        //Getting already played cards
        List<PlayingCard> trickCards = new ArrayList<>();
        List<PlayingCard[]> player0Tricks = board.getPlayer0Tricks();
        List<PlayingCard[]> player1Tricks = board.getPlayer1Tricks();

        for(PlayingCard[] trick: player0Tricks){
            trickCards.add(trick[0]);
            trickCards.add(trick[1]);
        }
        for(PlayingCard[] trick: player1Tricks){
            trickCards.add(trick[0]);
            trickCards.add(trick[1]);
        }

        //Check if "exchanged" trump card has been played
        //(if not, check if they are in the opposing players hand == not in my hand)
        if(oldTrumpCard != null) {

            boolean isLeading = (leadingCard != null && leadingCard.equals(oldTrumpCard));

            if (!trickCards.contains(oldTrumpCard) && !isLeading) {
                if (!playerCards.contains(oldTrumpCard)) {
                    otherPlayersCards.add(oldTrumpCard);
                }
            }
        }

        //check if opposing player played all of their declared marriageCards
        for(PlayingCard marriageCard: marriageCards){

            boolean isLeading =  (leadingCard != null && leadingCard.equals(marriageCard));

            if(!trickCards.contains(marriageCard) && !isLeading) {
                otherPlayersCards.add(marriageCard);
            }
        }

        //Reduce the number of unknown Cards by all findings

        //Remove players cards

        unknownCards.removeAll(playerCards);

        //Remove trumpCard
        unknownCards.remove(trumpCard);

        //Remove leadingCard
        if(leadingCard != null) {
            unknownCards.remove(leadingCard);
        }

        //Remove all of otherPlayers cards
        unknownCards.removeAll(otherPlayersCards);

        //Remove already played cards
        unknownCards.removeAll(trickCards);


        //Fill up all the hidden card-slots with random available cards

        //shuffle the unknown cards
        Collections.shuffle(unknownCards);

        int numberOfHandCards = playerCards.size();
        int numberOfOtherPlayersHandCards = numberOfHandCards;
        int cardsLeftInPile = board.playingCardsLeftInPile();

        //other player has one Card less, because he led the trick;
        if(leadingCard != null) {
            numberOfOtherPlayersHandCards--;
        }

        //first fill up the playingCardPile
        LinkedList<PlayingCard> playingCardPile = new LinkedList<>();


        for (int i = 0; i < cardsLeftInPile-1; i++) {
            playingCardPile.add(unknownCards.removeFirst());
        }

        // Add trumpCard as last if pile was not empty yet
        if(cardsLeftInPile > 0) {
            playingCardPile.addLast(trumpCard);
        } else {
            boolean inMyHand = playerCards.contains(trumpCard);
            boolean inTricks = trickCards.contains(trumpCard);
            boolean isLeading = (leadingCard != null && leadingCard.equals(trumpCard));
            boolean alreadyAdded = otherPlayersCards.contains(trumpCard);

            if(!inMyHand && !inTricks && !isLeading && !alreadyAdded) {
                otherPlayersCards.add(trumpCard);
            }
        }

        //the remaining cards should fit into the opponents hand
        if((otherPlayersCards.size() + unknownCards.size()) != numberOfOtherPlayersHandCards) {
            throw new IllegalStateException("Calculated wrong: Opponent has " +
                    (otherPlayersCards.size() + unknownCards.size()) +
                    " but should have " + numberOfOtherPlayersHandCards);
        } else {
            otherPlayersCards.addAll(unknownCards);
        }

        //create a board based on the findings and randomly assumed cards
        SchnapsenBoard filledBoard;
        if(playerId == 0) {
            filledBoard = new SchnapsenBoard(board, playerCards, otherPlayersCards, playingCardPile);
        } else {
            filledBoard = new SchnapsenBoard(board, otherPlayersCards, playerCards, playingCardPile);
        }

        return filledBoard;
    }

    private List<PlayingCard> generateFullDeck(SchnapsenBoard.cardSuits trumpSuit) {
        //creating and filling a list with of all not known cards
        LinkedList<PlayingCard> unknownCards = new LinkedList<>();

        unknownCards.add(new PlayingCard(SchnapsenBoard.cardSuits.SPADES, SchnapsenBoard.cardNames.JackOfSpades, 2));
        //Adding possible marriages to the spades cards
        PlayingCard queenSpades = new PlayingCard(SchnapsenBoard.cardSuits.SPADES, SchnapsenBoard.cardNames.QueenOfSpades, 3);
        PlayingCard kingSpades = new PlayingCard(SchnapsenBoard.cardSuits.SPADES, SchnapsenBoard.cardNames.KingOfSpades, 4);
        queenSpades.setPossibleMarriage(kingSpades);
        kingSpades.setPossibleMarriage(queenSpades);
        unknownCards.add(queenSpades);
        unknownCards.add(kingSpades);
        unknownCards.add(new PlayingCard(SchnapsenBoard.cardSuits.SPADES, SchnapsenBoard.cardNames.TenOfSpades, 10));
        unknownCards.add(new PlayingCard(SchnapsenBoard.cardSuits.SPADES, SchnapsenBoard.cardNames.AceOfSpades, 11));

        unknownCards.add(new PlayingCard(SchnapsenBoard.cardSuits.HEARTS, SchnapsenBoard.cardNames.JackOfHearts, 2));
        //Adding possible marriages to the hearts cards
        PlayingCard queenHearts = new PlayingCard(SchnapsenBoard.cardSuits.HEARTS, SchnapsenBoard.cardNames.QueenOfHearts, 3);
        PlayingCard kingHearts = new PlayingCard(SchnapsenBoard.cardSuits.HEARTS, SchnapsenBoard.cardNames.KingOfHearts, 4);
        queenHearts.setPossibleMarriage(kingHearts);
        kingHearts.setPossibleMarriage(queenHearts);
        unknownCards.add(queenHearts);
        unknownCards.add(kingHearts);
        unknownCards.add(new PlayingCard(SchnapsenBoard.cardSuits.HEARTS, SchnapsenBoard.cardNames.TenOfHearts, 10));
        unknownCards.add(new PlayingCard(SchnapsenBoard.cardSuits.HEARTS, SchnapsenBoard.cardNames.AceOfHearts, 11));

        unknownCards.add(new PlayingCard(SchnapsenBoard.cardSuits.DIAMONDS, SchnapsenBoard.cardNames.JackOfDiamonds, 2));
        //Adding possible marriages to the hearts cards
        PlayingCard queenDiamonds = new PlayingCard(SchnapsenBoard.cardSuits.DIAMONDS, SchnapsenBoard.cardNames.QueenOfDiamonds, 3);
        PlayingCard kingDiamonds = new PlayingCard(SchnapsenBoard.cardSuits.DIAMONDS, SchnapsenBoard.cardNames.KingOfDiamonds, 4);
        queenDiamonds.setPossibleMarriage(kingDiamonds);
        kingDiamonds.setPossibleMarriage(queenDiamonds);
        unknownCards.add(queenDiamonds);
        unknownCards.add(kingDiamonds);
        unknownCards.add(new PlayingCard(SchnapsenBoard.cardSuits.DIAMONDS, SchnapsenBoard.cardNames.TenOfDiamonds, 10));
        unknownCards.add(new PlayingCard(SchnapsenBoard.cardSuits.DIAMONDS, SchnapsenBoard.cardNames.AceOfDiamonds, 11));

        unknownCards.add(new PlayingCard(SchnapsenBoard.cardSuits.CLUBS, SchnapsenBoard.cardNames.JackOfClubs, 2));
        //Adding possible marriages to the hearts cards
        PlayingCard queenClubs = new PlayingCard(SchnapsenBoard.cardSuits.CLUBS, SchnapsenBoard.cardNames.QueenOfClubs, 3);
        PlayingCard kingClubs = new PlayingCard(SchnapsenBoard.cardSuits.CLUBS, SchnapsenBoard.cardNames.KingOfClubs, 4);
        queenClubs.setPossibleMarriage(kingClubs);
        kingClubs.setPossibleMarriage(queenClubs);
        unknownCards.add(queenClubs);
        unknownCards.add(kingClubs);
        unknownCards.add(new PlayingCard(SchnapsenBoard.cardSuits.CLUBS, SchnapsenBoard.cardNames.TenOfClubs, 10));
        unknownCards.add(new PlayingCard(SchnapsenBoard.cardSuits.CLUBS, SchnapsenBoard.cardNames.AceOfClubs, 11));

        for(PlayingCard card :  unknownCards){
            if(trumpSuit == card.getSuit()){
                card.setIsTrumpSuit(true);
            }
        }
        return unknownCards;
    }

    /*private PlayingCard findCardInPile(List<PlayingCard> pile, PlayingCard targetCard){
        if(targetCard.getCardName() == SchnapsenBoard.cardNames.PlaceHolder) {
            return new PlayingCard(SchnapsenBoard.cardSuits.SPADES, SchnapsenBoard.cardNames.PlaceHolder,0);
        }
        return pile.stream().filter(p -> p.equals(targetCard)).findFirst().orElse(null);
    }*/
/*
    private int calculateMaxDepth(Schnapsen schnapsen)
    {
        int maxDepth = 0;
        SchnapsenBoard currentBoard = schnapsen.getBoard();

        //all the players cards are an action
        if(schnapsen.getCurrentPlayer() == 0)
        {
            maxDepth += currentBoard.getPlayer0Cards().size() *2;
        } else
        {
            maxDepth += currentBoard.getPlayer1Cards().size() *2;
        }

        //exchange trump is an action
        if(currentBoard.getOldTrumpCard()!=null)
        {
            maxDepth ++;
        }
        //close talon is an action
        if(!currentBoard.isTalonClosed())
        {
            maxDepth ++;
            //remaining cards in pile are an option
            maxDepth += currentBoard.playingCardsLeftInPile();
        }
        //if player was not leading it is one action less
        if(currentBoard.getLeadingCard() != null)
        {
            maxDepth --;
        }
        //possibly 4 marriages as an action minus the ones declared already
        int player1MarriageCount = currentBoard.getPlayer1Marriages().size()/2;
        int player0MarriageCount = currentBoard.getPlayer0Marriages().size()/2;
        maxDepth+=4-(player0MarriageCount+player1MarriageCount);

        return maxDepth;

    }
*/

    private boolean isRoundOver(Schnapsen schnapsen){
        long oldBummerlValuePlayer0 = (long) oldUtilityPlayer0;
        long oldBummerlValuePlayer1 = (long) oldUtilityPlayer1;
        long newBummerlValuePlayer0 = (long) schnapsen.getUtilityValue(0);
        long newBummerlValuePlayer1 =  (long) schnapsen.getUtilityValue(1);

        if(oldBummerlValuePlayer0 != newBummerlValuePlayer0 || oldBummerlValuePlayer1 != newBummerlValuePlayer1)
        {
            return true;
        }

        return false;
    }

}
