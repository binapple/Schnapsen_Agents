package mcts_agent;

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

public class Mcts_Agent_Schnapsen extends AbstractGameAgent<Schnapsen, SchnapsenAction> implements GameAgent<Schnapsen, SchnapsenAction> {

    //This constant is used for the UCT formula
    private final double MCTS_EXPLORATION = Math.sqrt(2);

    //These variables are used for the round end check
    private double oldUtilityPlayer0;
    private double oldUtilityPlayer1;

    /**
     * Constructor for the Strategy Game Engine
     * @param log a logger object passed by the engine
     */
    public Mcts_Agent_Schnapsen(Logger log) {
        super(log);
    }

    /**
     * Constructor for testing without a logger object
     */
    public Mcts_Agent_Schnapsen() {
        super();
    }

    /**
     * This method will be called by the engine everytime the agent has its turn.
     * In this method the agent looks at one possible determinization and runs an MCTS algorithm on it
     * @param schnapsen the games state as given by the engine (may include hidden information)
     * @param l the maximum available time the agent is allowed to take to think about its next action
     * @param timeUnit the unit in which the l parameter is measured
     * @return a  SchnapsenAction chosen from the agents available ones after determinizing the imperfect information once and running a UCT algorithm
     */
    @Override
    public SchnapsenAction computeNextAction(Schnapsen schnapsen, long l, TimeUnit timeUnit) {

        SchnapsenBoard board = schnapsen.getBoard();
        Set<SchnapsenAction> availableActions = schnapsen.getPossibleActions();

        //single choice actions can be returned immediately
        if(availableActions.size() == 1){
            return availableActions.iterator().next();
        }

        //This method is provided by the AbstractGame interface and used to track remaining computation time with shouldStopComputation()
        setTimers(l,  timeUnit);

        //set the utility values for end of round check in simulations
        oldUtilityPlayer0 = schnapsen.getUtilityValue(0);
        oldUtilityPlayer1 = schnapsen.getUtilityValue(1);

        //This list is a one time generation of the cards in the game, setting the boolean on trump card to true if suit matches
        List<PlayingCard> deckOfCards = generateFullDeck(board.getTrumpCard().getSuit());

        //This board is a new board based on the newly generated Information, which does not include any more hidden Information and is therefore playable
        SchnapsenBoard generatedBoard = generateMissingInformation(board, deckOfCards);
        //We also need to generate a new Schnapsen object with the new board
        Schnapsen generatedSchnapsen = new Schnapsen(generatedBoard);

        //The root node used to track the tree
        Mcts_Node_Schnapsen rootNode = new  Mcts_Node_Schnapsen(null, null, generatedSchnapsen);

        //used for tracking iterations
        int iteration = 0;
        //starting the MCTS Algorithm -> it will run as long as possible (given the time constraint)
        while(!shouldStopComputation())
        {
            Mcts_Node_Schnapsen selectedNode = selectNode(rootNode);
            Mcts_Node_Schnapsen expandedNode = expandNode(selectedNode);
            double simulationScore = simulateNode(expandedNode);
            backPropagateNode(expandedNode, simulationScore);
            iteration++;
        }
        log._debugf("MCTS completed %d iterations", iteration);

        //finding out which action was the best and returning it, based on the most robust child:
        //The child with the most visits
        log.debug("--- Action Statistics ---");
        SchnapsenAction bestAction = null;
        Mcts_Node_Schnapsen bestNode = null;
        if(!rootNode.getChildNodes().isEmpty())
        {
            int visits = -1;
            for(Mcts_Node_Schnapsen child : rootNode.getChildNodes())
            {
                double winRate = (child.getVisitations() > 0) ? (child.getScore() / child.getVisitations()) : 0.0;
                log._debugf("Action: %-20s | Visits: %6d | WinRate: %5.2f%%",
                        child.getParentAction().toString(),
                        child.getVisitations(),
                        winRate * 100.0);
                if(child.getVisitations() > visits)
                {
                    visits = child.getVisitations();
                    bestAction = child.getParentAction();
                    bestNode = child;
                }
            }
        }

        //Fallback if the time was too short to pick an action by MCTS -> just pick the first available action
        if(bestAction == null)
        {
            return availableActions.iterator().next();
        }
        double expectedWinRate = (bestNode.getVisitations() > 0) ? (bestNode.getScore() / bestNode.getVisitations()) : 0.0;
        log._debugf("--> CHOSEN ACTION: %s (Expected Win Rate: %.2f%%)", bestAction.toString(), expectedWinRate * 100.0);
        return bestAction;
    }

    /**
     * This method uses the score of the simulation and adds it to the nodes score. The visitation counter is also incremented.
     * This is repeated for all the nodes parents to update all involved nodes accordingly
     * @param expandedNode the node which was last expanded and received the simulationScore
     * @param simulationScore the score of the playout simulation for this node
     */
    private void backPropagateNode(Mcts_Node_Schnapsen expandedNode, double simulationScore) {
        Mcts_Node_Schnapsen propagationNode = expandedNode;
        while (propagationNode != null) {
            propagationNode.incrementVisitations();
            propagationNode.addScore(simulationScore);
            propagationNode = propagationNode.getParentNode();
        }
    }

    /**
     * This method uses the expandedNode to simulate a complete playthrough till the end of the current round, end of game or the end of calculation time budget
     * @param expandedNode the last expanded node
     * @return a score in the range of 0 and 1, where 1 is a win and 0 is a loss. In between scores represent the score difference to the opposing player in an ongoing round
     */
    private double simulateNode(Mcts_Node_Schnapsen expandedNode) {
        //randomly play actions until game or round is over
        Schnapsen currentGame = expandedNode.getGame();
        while(!shouldStopComputation() && !currentGame.isGameOver() && !isRoundOver(currentGame)) {
            Set<SchnapsenAction> possibleActions = currentGame.getPossibleActions();
            SchnapsenAction action = Util.selectRandom(possibleActions);
            currentGame = (Schnapsen) currentGame.doAction(action);
        }

        return simulationScore(currentGame);
    }

    /**
     * This method calculates a score in the range of 0.0 and 1.0 to represent the winning state of the agents player
     * @param currentGame The Schnapsen games state that needs calculation
     * @return a score representing either a win (1.0) or a loss (0.0). If the round is not over the score is based on the difference in the current round score of the players
     */
    private double simulationScore(Schnapsen currentGame) {
        SchnapsenBoard board = currentGame.getBoard();
        //If game is over we just have to check if our agents player has won, therefore not reached the maximum Bummerl points
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
            //Special case where we look at the difference in scores in the ongoing round
            double myUtilValue = currentGame.getUtilityValue(playerId) % 1.0;
            double theirUtilValue = currentGame.getUtilityValue(1-playerId) % 1.0;
            double utilValue = (myUtilValue - theirUtilValue + 1.0) / 2.0;
            return utilValue;
        } else {
            //Here we check who won the last round
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

    /**
     * This method expands a node and returns one of its untried children if there are any
     * @param selectedNode the node which was selected by the UCT algorithm
     * @return a newly expanded child node if expansion happened, or the node itself if otherwise
     */
    private Mcts_Node_Schnapsen expandNode(Mcts_Node_Schnapsen selectedNode) {
            if(selectedNode.getGame().isGameOver() || isRoundOver(selectedNode.getGame())) {
                return selectedNode;
            }
            if(!selectedNode.isCompletelyExpanded()) {
                return selectedNode.getUntriedChild();
            }
            else {
                return selectedNode;
            }
    }

    /**
     * This method implements the selection process. Starting from the root node we check each of the children based on the UCT formula
     * and select the best one. This is repeated till we find a leaf node (where game or round ends) or reaching an unexpanded node.
     * <p>
     * When reaching an unexpanded node (not all children have been tried yet) or reaching end of round/end of game/end of computation time we stop the selection.
     * @param rootNode the node where our search tree starts
     * @return a node which has unexpanded children or a leaf node, or a node that could not be entirely checked because of time constraints
     */
    private Mcts_Node_Schnapsen selectNode(Mcts_Node_Schnapsen rootNode) {
        Mcts_Node_Schnapsen selectedNode = rootNode;
        while (selectedNode.isCompletelyExpanded() && !selectedNode.getGame().isGameOver() && !isRoundOver(selectedNode.getGame())&& !shouldStopComputation()) {
            boolean opponentAction = selectedNode.getGame().getCurrentPlayer()!=this.playerId;
            double bestUCT = Double.MIN_VALUE;
            for(Mcts_Node_Schnapsen child : selectedNode.getChildNodes()) {
                double currentUCT = getUCT(child,selectedNode,opponentAction);
                if(currentUCT > bestUCT) {
                    selectedNode = child;
                    bestUCT = currentUCT;
                }
            }
        }
        return selectedNode;
    }

    /**
     * Here we use the UCT formula to steer our selection process. We expect our opponent to choose the action that is worst for us
     * @param child a child node of a fully expanded node
     * @param parent the parent node from which we want to select the best fitting child based on our formula
     * @param opponentAction a boolean that states if the action is from the enemy player
     * @return a score based on the UCT formula for the current child
     */
    private double getUCT(Mcts_Node_Schnapsen child, Mcts_Node_Schnapsen parent, boolean opponentAction) {
        //unvisited children should be prioritized
        if(child.getVisitations() < 1)
        {
            return Double.POSITIVE_INFINITY;
        }

        //This is the exploitation part of the UCT formula -> the better this part the more often it will be chosen
        double exploitationPart = child.getScore() / child.getVisitations();

        //To calculate opponent actions we calculate a score inverted.
        //The better the score would be for us, the worse it is for selection purposes, if it is the enemy's turn
        if(opponentAction) {
            exploitationPart = 1.0 - exploitationPart;
        }

        //This part helps the tree to not be too focused on winning branches and explore different scenarios. With more simulations this part gets less significance
        double explorationPart = MCTS_EXPLORATION * Math.sqrt(Math.log(parent.getVisitations()) / (child.getVisitations()));

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

        //Getting both players cards that have been taken in a past trick
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

            if(!trickCards.contains(marriageCard) && !isLeading && !otherPlayersCards.contains(marriageCard)) {
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

        //other player has one Card less, because they led the trick
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

    /**
     * This method is used to generate a full deck. The trump suit is used to set the trump status for cards with matching suits
     * @param trumpSuit the trump suit of the current round
     * @return a complete deck of cards with correctly set cards of trump status
     */
    private List<PlayingCard> generateFullDeck(SchnapsenBoard.cardSuits trumpSuit) {
        //creating and filling a list with of all not known cards
        LinkedList<PlayingCard> deckOfCards = new LinkedList<>();

        deckOfCards.add(new PlayingCard(SchnapsenBoard.cardSuits.SPADES, SchnapsenBoard.cardNames.JackOfSpades, 2));
        //Adding possible marriages to the spades cards
        PlayingCard queenSpades = new PlayingCard(SchnapsenBoard.cardSuits.SPADES, SchnapsenBoard.cardNames.QueenOfSpades, 3);
        PlayingCard kingSpades = new PlayingCard(SchnapsenBoard.cardSuits.SPADES, SchnapsenBoard.cardNames.KingOfSpades, 4);
        queenSpades.setPossibleMarriage(kingSpades);
        kingSpades.setPossibleMarriage(queenSpades);
        deckOfCards.add(queenSpades);
        deckOfCards.add(kingSpades);
        deckOfCards.add(new PlayingCard(SchnapsenBoard.cardSuits.SPADES, SchnapsenBoard.cardNames.TenOfSpades, 10));
        deckOfCards.add(new PlayingCard(SchnapsenBoard.cardSuits.SPADES, SchnapsenBoard.cardNames.AceOfSpades, 11));

        deckOfCards.add(new PlayingCard(SchnapsenBoard.cardSuits.HEARTS, SchnapsenBoard.cardNames.JackOfHearts, 2));
        //Adding possible marriages to the hearts cards
        PlayingCard queenHearts = new PlayingCard(SchnapsenBoard.cardSuits.HEARTS, SchnapsenBoard.cardNames.QueenOfHearts, 3);
        PlayingCard kingHearts = new PlayingCard(SchnapsenBoard.cardSuits.HEARTS, SchnapsenBoard.cardNames.KingOfHearts, 4);
        queenHearts.setPossibleMarriage(kingHearts);
        kingHearts.setPossibleMarriage(queenHearts);
        deckOfCards.add(queenHearts);
        deckOfCards.add(kingHearts);
        deckOfCards.add(new PlayingCard(SchnapsenBoard.cardSuits.HEARTS, SchnapsenBoard.cardNames.TenOfHearts, 10));
        deckOfCards.add(new PlayingCard(SchnapsenBoard.cardSuits.HEARTS, SchnapsenBoard.cardNames.AceOfHearts, 11));

        deckOfCards.add(new PlayingCard(SchnapsenBoard.cardSuits.DIAMONDS, SchnapsenBoard.cardNames.JackOfDiamonds, 2));
        //Adding possible marriages to the hearts cards
        PlayingCard queenDiamonds = new PlayingCard(SchnapsenBoard.cardSuits.DIAMONDS, SchnapsenBoard.cardNames.QueenOfDiamonds, 3);
        PlayingCard kingDiamonds = new PlayingCard(SchnapsenBoard.cardSuits.DIAMONDS, SchnapsenBoard.cardNames.KingOfDiamonds, 4);
        queenDiamonds.setPossibleMarriage(kingDiamonds);
        kingDiamonds.setPossibleMarriage(queenDiamonds);
        deckOfCards.add(queenDiamonds);
        deckOfCards.add(kingDiamonds);
        deckOfCards.add(new PlayingCard(SchnapsenBoard.cardSuits.DIAMONDS, SchnapsenBoard.cardNames.TenOfDiamonds, 10));
        deckOfCards.add(new PlayingCard(SchnapsenBoard.cardSuits.DIAMONDS, SchnapsenBoard.cardNames.AceOfDiamonds, 11));

        deckOfCards.add(new PlayingCard(SchnapsenBoard.cardSuits.CLUBS, SchnapsenBoard.cardNames.JackOfClubs, 2));
        //Adding possible marriages to the hearts cards
        PlayingCard queenClubs = new PlayingCard(SchnapsenBoard.cardSuits.CLUBS, SchnapsenBoard.cardNames.QueenOfClubs, 3);
        PlayingCard kingClubs = new PlayingCard(SchnapsenBoard.cardSuits.CLUBS, SchnapsenBoard.cardNames.KingOfClubs, 4);
        queenClubs.setPossibleMarriage(kingClubs);
        kingClubs.setPossibleMarriage(queenClubs);
        deckOfCards.add(queenClubs);
        deckOfCards.add(kingClubs);
        deckOfCards.add(new PlayingCard(SchnapsenBoard.cardSuits.CLUBS, SchnapsenBoard.cardNames.TenOfClubs, 10));
        deckOfCards.add(new PlayingCard(SchnapsenBoard.cardSuits.CLUBS, SchnapsenBoard.cardNames.AceOfClubs, 11));

        for(PlayingCard card :  deckOfCards){
            if(trumpSuit == card.getSuit()){
                card.setIsTrumpSuit(true);
            }
        }
        return deckOfCards;
    }

    /**
     * This check is used to find out if since the last action taken a new round has started on the board.
     * This is used to not simulate into new rounds as the new round shuffles new cards,
     * but the random object of an agents view is not synchronised with the actual boards random object.
     * Therefore, it is meaningless to simulate into upcoming rounds.
     * @param schnapsen the games state that is compared to the state of the game passed by the engine
     * @return a boolean that states if the games has started into a different round than the one passed by the engine
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
