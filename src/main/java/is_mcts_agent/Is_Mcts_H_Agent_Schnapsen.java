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

public class Is_Mcts_H_Agent_Schnapsen extends AbstractGameAgent<Schnapsen, SchnapsenAction> implements GameAgent<Schnapsen, SchnapsenAction> {

    //This constant is used for the UCT formula
    private final double MCTS_EXPLORATION = Math.sqrt(2);

    //epsilon-greedy limit if heuristics should be used or random simulation should occur
    private static double EPSILON_GREEDY = 0.3;

    //These variables are used for the end of round check
    private double oldUtilityPlayer0;
    private double oldUtilityPlayer1;

    //used for Tree re-use
    private Is_Mcts_Node_Schnapsen currentRootNode;

    /**
     * Constructor for the Strategy Game Engine
     * @param log a logger object passed by the engine
     */
    public Is_Mcts_H_Agent_Schnapsen(Logger log) {
        super(log);
    }

    /**
     * Constructor for testing without a logger object
     */
    public Is_Mcts_H_Agent_Schnapsen() {
        super();
    }

    /**
     * This method will be called by the engine everytime the agent has its turn.
     * In this method the agent creates a new determinization every iteration and creates information sets.
     * In such a set the participating nodes statistics are saved throughout the determinization.
     * Over a period of time all possible constellations will be looked at and the best action over all of these possible scenarios is chosen.
     * As not each action is available all the time (based on the determination) we also track the availability of each node, which is included in the selection process.
     * <p>
     * This algorithm also includes heuristics in the simulation phase that get chosen based on an epsilon-greedy percentage.
     * @param schnapsen the games state as given by the engine (may include hidden information)
     * @param l the maximum available time the agent is allowed to take to think about its next action
     * @param timeUnit the unit in which the l parameter is measured
     * @return a  SchnapsenAction chosen from the agents available ones after running an Information Set MCTS algorithm
     */
    @Override
    public SchnapsenAction computeNextAction(Schnapsen schnapsen, long l, TimeUnit timeUnit) {

        SchnapsenBoard board = schnapsen.getBoard();
        Set<SchnapsenAction> availableActions = schnapsen.getPossibleActions();

        //set the utility values for end of round check in simulations
        oldUtilityPlayer0 = schnapsen.getUtilityValue(0);
        oldUtilityPlayer1 = schnapsen.getUtilityValue(1);

        //single choice actions can be returned immediately
        if(availableActions.size() == 1){
            return availableActions.iterator().next();
        }

        //This method is provided by the AbstractGame interface and used to track remaining computation time with shouldStopComputation()
        setTimers(l,  timeUnit);

        // Detect if this is the start of a completely new Round
        boolean isNewRound = board.getPlayer0Score() == 0 && board.getPlayer1Score() == 0
                && board.getPlayer0Tricks().isEmpty() && board.getPlayer1Tricks().isEmpty();

        //Here we do a first round check and relocate the root node if necessary
        if(this.currentRootNode == null || isNewRound) {
            this.currentRootNode = new Is_Mcts_Node_Schnapsen(null, null);
        } else {
            //if it was not the first round we re-use the tree but have to change the root
            SchnapsenAction lastAction = schnapsen.getPreviousAction();
            if(lastAction  != null) {

                //to re-use the tree when we have repeat turns (marriage or closing talon) or won a trick after not leading we do not have to change the root
                //as we should be on the correct one already
                boolean isAgainOurTurn = this.currentRootNode.getParentAction() != null && this.currentRootNode.getParentAction().equals(lastAction);

                if(isAgainOurTurn)
                {
                    log._debugf("Repeated turn detected. Root is already correct!");
                } else {
                    //We have to update our trees root to the current state of the board
                    Is_Mcts_Node_Schnapsen nextRootNode = this.currentRootNode.findChildWithAction(lastAction);
                    if (nextRootNode != null) {
                        //When we found the action in our tre we cut off the parent (and this is our new root)
                        log._debugf("Reusing tree! Found opponent action: %s. Starting with %d prior visits.",
                                lastAction.toString(), nextRootNode.getVisitations());
                        this.currentRootNode = nextRootNode;
                        this.currentRootNode.setParentNode(null);
                    } else {
                        //if the action has not yet been simulated in the tree we start with an empty tree
                        log._debugf("Tree reuse failed. Opponent action '%s' was never simulated. Starting fresh.", lastAction.toString());
                        this.currentRootNode = new Is_Mcts_Node_Schnapsen(null, null);
                    }
                }
            }
        }

        //we can prune actions that are now not available with the updated board states
        int prunedActions = 0;

        Iterator<Is_Mcts_Node_Schnapsen> iterator = this.currentRootNode.getChildNodes().iterator();

        //We look at every stored action of this node and prune the ones that are not actually available
        while(iterator.hasNext()) {
            Is_Mcts_Node_Schnapsen currentChildNode = iterator.next();
            if(!availableActions.contains(currentChildNode.getParentAction())) {
                iterator.remove();
                prunedActions++;
            }
        }

        if(prunedActions > 0) {
            log._debugf("Filtering the root node: Pruned %d actions from re-used tree.", prunedActions);
        }

        //This list is a one time generation of the cards in the game, setting the boolean on trump card to true if suit matches
        List<PlayingCard> deckOfCards = generateFullDeck(board.getTrumpCard().getSuit());


        //used for keeping track of iterations = how many simulations could be run
        int iterations = 0;
        //We are starting the IS-MCTS Algorithm -> it will run as long as possible
        while(!shouldStopComputation())
        {
            //Create a random determinization of the available board for each iteration
            SchnapsenBoard generatedBoard = generateMissingInformation(board,  deckOfCards);
            //We also need to generate a new Schnapsen object with the new board
            Schnapsen generatedSchnapsen = new Schnapsen(generatedBoard);

            //We select a new node through this method, with the adapted selection formula for ISMCTS and expand it if necessary
            //The generated Schnapsen state matches the one of the selected/expanded node
            Is_Mcts_Node_Schnapsen expandedNode = selectAndExpand(this.currentRootNode, generatedSchnapsen); //, maxDepth, depth);
            //For this altered game state we simulate a playout and get a score
            double simulationScore = simulateNode(generatedSchnapsen); // depthTillRoundEnd);
            //The score needs to be propagated to all participating nodes and their parents
            backPropagateNode(expandedNode, simulationScore);
            iterations++;
        }

        log.debugf("IS-MCTS completed %d iterations", iterations);

        //Here we are finding out which action was the best and returning it
        //For this purpose we choose the child with the most visits, the most robust child
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

        //fallback if ISMCTS did not find an action we choose the first possible one
        if(bestAction == null)
        {
            bestAction = availableActions.iterator().next();
        }

        //After the algorithm we advance our game tree based on our best action chosen
        Is_Mcts_Node_Schnapsen bestMoveNode = this.currentRootNode.findChildWithAction(bestAction);

        //Log what action we chose and the expected win rate of our action
        if(bestMoveNode != null)
        {
            double expectedWinRate = (bestMoveNode.getVisitations() > 0) ? (bestMoveNode.getScore() / bestMoveNode.getVisitations()) : 0.0;
            log._debugf("--> CHOSEN ACTION: %s (Expected Win Rate: %.2f%%)", bestAction.toString(), expectedWinRate * 100.0);
            this.currentRootNode = bestMoveNode;
            this.currentRootNode.setParentNode(null); // reset the parent to create a new root
        }
        else
        {
            //we have to start again because there have not yet been simulations for our chosen action
            //This is a fallback if we could not determine an action
            this.currentRootNode = new Is_Mcts_Node_Schnapsen(null, null);
        }

        return bestAction;
    }

    /**
     * This method uses the score of the simulation and adds it to the nodes score. The visitation counter is also incremented.
     * This is repeated for all the nodes parents to update all involved nodes accordingly
     * @param expandedNode the node which was last expanded and received the simulationScore
     * @param simulationScore the score of the playout simulation for this node
     */
    private void backPropagateNode(Is_Mcts_Node_Schnapsen expandedNode, double simulationScore) {
        Is_Mcts_Node_Schnapsen propagationNode = expandedNode;
        while (propagationNode != null) {
            propagationNode.incrementVisitations();
            propagationNode.addScore(simulationScore);
            propagationNode = propagationNode.getParentNode();
        }
    }

    /**
     * This method uses the current games state to simulate a complete playthrough till the end of the current round, end of game or the end of calculation time budget
     * <p>
     * We use an epsilon greedy approach, where we play our heuristic action if the random number between 0.0 and 1.0 is higher than EPSILON_GREEDY.
     * Therefore, if EPSILON_GREEDY is 0.3 we choose the heuristic action about ~70% of the time.
     * @param schnapsen the games current state
     * @return a score in the range of 0 and 1, where 1 is a win and 0 is a loss. In between scores represent the score difference to the opposing player in an ongoing round
     */
    private double simulateNode(Schnapsen schnapsen) { //,  int  maxDepth) {

        //randomly play actions
        while(!shouldStopComputation() && !schnapsen.isGameOver() && !this.isRoundOver(schnapsen)){ //&& simulationDepth < maxDepth){

            // Here we add our heuristic playout based on the epsilon greedy approach
            Set<SchnapsenAction> possibleActions = schnapsen.getPossibleActions();

            SchnapsenAction playoutAction;
            if(Math.random() < EPSILON_GREEDY) {
                // we select a random action
                playoutAction = Util.selectRandom(possibleActions);
            }
            else {
                // Here we let the algorithm choose the heuristical playout
                playoutAction = getHeuristicAction(schnapsen);
            }

            //We have to apply the action to the board on either choice
            schnapsen = (Schnapsen) schnapsen.doAction(playoutAction);
        }

        return simulationScore(schnapsen);
    }

    /**
     * This method checks the current games situation for the best action based on some rules (heuristics)
     * @param playoutSchnapsen the current game state
     * @return an action out of the current game state, selected based on some rules
     */
    private SchnapsenAction getHeuristicAction(Schnapsen playoutSchnapsen) {
        Set<SchnapsenAction> possibleActions = playoutSchnapsen.getPossibleActions();

        //if only one action is available instantly return it
        if(possibleActions.size() == 1) {
            return possibleActions.iterator().next();
        }

        int playerId = playoutSchnapsen.getCurrentPlayer();
        SchnapsenBoard board =  playoutSchnapsen.getBoard();
        PlayingCard leadingCard = board.getLeadingCard();
        PlayingCard trumpCard = board.getTrumpCard();
        List<PlayingCard> playersCards = new ArrayList<>();
        if(playerId == 0)
        {
            playersCards = board.getPlayer0Cards();
        }   else
        {
            playersCards = board.getPlayer1Cards();
        }

        //We check if we are the following player or the leading one
        boolean selfLeading = false;
        if(leadingCard == null){
            selfLeading = true;
        }

        // Rule 1: Always exchange trump or declare marriage if possible (only possible if leading)
        if(selfLeading)
        {
            for(SchnapsenAction action : possibleActions) {
                if(action.toString().contains("Marriage") || action.toString().contains("Exchange")) {
                    return action;
                }
            }

            //Rule 2: Passive Leading with a low valued non-trump card
            PlayingCard lowestLead = null;
            for(PlayingCard playerCard : playersCards) {
                if(!playerCard.getSuit().equals(trumpCard.getSuit())) {
                    if(lowestLead == null || playerCard.getCardValue() < lowestLead.getCardValue()) {
                        lowestLead = playerCard;
                    }
                }
            }
            if(lowestLead != null) {
                for (SchnapsenAction action : possibleActions) {
                    if (action.toString().contains(lowestLead.toString())) {
                        return action;
                    }
                }
            }
        } else {
            //Rule 3: try taking 10 or Aces from non-trump color by also using trump cards
            PlayingCard takeTA = null;
            if(!board.isTalonClosed())
            {
                if(leadingCard.getCardValue() >= 10 && leadingCard.getSuit() != trumpCard.getSuit()) {
                    for (PlayingCard playerCard : playersCards) {
                        //If having the Ace over the Ten just take the trick with the Ace
                        if( playerCard.getSuit().equals(leadingCard.getSuit()) && playerCard.getCardValue() > leadingCard.getCardValue()) {
                            takeTA = playerCard;
                            break;
                        }

                        //we use the lowest trump to take the Ten or Ace
                        if(playerCard.getSuit().equals(trumpCard.getSuit())) {
                            if(takeTA == null || playerCard.getCardValue() < takeTA.getCardValue()) {
                                takeTA = playerCard;
                            }
                        }
                    }
                }
            }

            if(takeTA != null) {
                for(SchnapsenAction action : possibleActions) {
                    if(action.toString().contains(takeTA.toString())) {
                        return action;
                    }
                }
            }
        }

        // Fallback if no rule is applied we select a random action
        return Util.selectRandom(possibleActions);
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
     * This method tracks the availability of the actions and therefore nodes.
     * Then it tries to find newly untried actions of the node (starting from the root node).
     * If there are no untried actions we continue finding the best fit child based on the modified UCT formula for Information Sets.
     * We repeat this process for every newly selected node until we reach the game or round end or should stop the computation because we reach the end of the budget.
     *
     * @param rootNode the trees root node, where we start the search or selection process
     * @param schnapsen the games current state
     * @return the node we expanded or a leaf node, or a node that could not be entirely checked because of time constraints
     */
    private Is_Mcts_Node_Schnapsen selectAndExpand(Is_Mcts_Node_Schnapsen rootNode, Schnapsen schnapsen) { //, int maxDepth, int[] depth) {
        Is_Mcts_Node_Schnapsen selectedNode = rootNode;
        SchnapsenBoard board = schnapsen.getBoard();

        //When reaching a leaf we can instantly return
        if(schnapsen.isGameOver() || this.isRoundOver(schnapsen))
        {
            return selectedNode;
        }

        while(!schnapsen.isGameOver() && !this.isRoundOver(schnapsen) && !shouldStopComputation()) {
            //Here we check for possible actions in this game state, track actions that have not been tried
            //and ones which are already part of the tree
            Set<SchnapsenAction> possibleActions = schnapsen.getPossibleActions();

            //increment availability counts of all possible actions and therefore available children
            for(Is_Mcts_Node_Schnapsen child : selectedNode.getChildNodes())
            {
                if (possibleActions.contains(child.getParentAction())) {
                    child.incrementAvailabilityCount();
                }
            }

            //We track the untried actions and the nodes children who have a corresponding action
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

            //Here we expand not yet tried actions by randomly choosing one if there are more available to choose and applying it to the games state
            if(!notTriedActions.isEmpty()) {
                SchnapsenAction chosenAction = Util.selectRandom(notTriedActions);
                Is_Mcts_Node_Schnapsen expandedNode = new  Is_Mcts_Node_Schnapsen(chosenAction, selectedNode);
                selectedNode.addChild(expandedNode);

                chosenAction.doAction(board);
                return expandedNode;

            }

            //If there was no expansion we have to select the child with the best fitting UCT criteria
            //In the step before we already filled a list for the children who have available actions

            //We once more have to check if we are playing or the opposing player has their turn
            boolean opponentAction = schnapsen.getCurrentPlayer() != this.playerId;
            double bestUCT = Double.MIN_VALUE;
            for (Is_Mcts_Node_Schnapsen child : childrenWithAction) {
                double currentUCT = getUCT(child, opponentAction);
                if (currentUCT > bestUCT) {
                    selectedNode = child;
                    bestUCT = currentUCT;
                }
            }

            //After the selection of the best UCT fitting child we apply their action to the game
            selectedNode.getParentAction().doAction(board);

        }

        //We return the last selected node (should be a leaf node) at the end of round or game.
        //When the time runs out we also return the last selected node
        return selectedNode;
    }

    /**
     * Here we use the adapted UCT formula for ISMCTS to steer our selection process. We expect our opponent to choose the action that is worst for us
     * @param child a child node of a fully expanded node
     * @param opponentAction a boolean that states if the action is from the enemy player
     * @return a score based on the UCT formula for the current child
     */
    private double getUCT(Is_Mcts_Node_Schnapsen child, boolean opponentAction) {
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
        //Here we also include the availability of the node. If it was available often but never visited it means it has not been explored often.
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
