package iimc_Agent_Schnapsen;

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

public class Iimc_H_Agent_Schnapsen extends AbstractGameAgent<Schnapsen, SchnapsenAction> implements GameAgent<Schnapsen, SchnapsenAction> {

    //These variables are used for the end of round check
    private double oldUtilityPlayer0;
    private double oldUtilityPlayer1;

    //epsilon-greedy limit if heuristics should be used or random simulation should occur
    private static double EPSILON_GREEDY = 0.3;

    /**
     * Constructor for the Strategy Game Engine
     * @param log a logger object passed by the engine
     */
    public Iimc_H_Agent_Schnapsen(Logger log) {
        super(log);
    }

    /**
     * This method will be called by the engine everytime the agent has its turn.
     * In this method the agent creates a new determinization every iteration. We do not use a tree in this algorithm but store the
     * visits and scores to each possible action. The simulation or playout starts from the possible actions.
     * Through many iterations we can then calculate an average score for each action based on all information gathered in the determinized "worlds".
     * Additionally, we give the playouts a chance based on EPSILON_GREEDY to include certain playout heuristics.
     * @param schnapsen the games state as given by the engine (may include hidden information)
     * @param l the maximum available time the agent is allowed to take to think about its next action
     * @param timeUnit the unit in which the l parameter is measured
     * @return a  SchnapsenAction chosen from the agents available ones after running an Imperfect Information Monte Carlo algorithm
     */
    @Override
    public SchnapsenAction computeNextAction(Schnapsen schnapsen, long l, TimeUnit timeUnit) {

        SchnapsenBoard board = schnapsen.getBoard();
        Set<SchnapsenAction> availableActions = schnapsen.getPossibleActions();

        // Single choice actions can be returned immediately
        if (availableActions.size() == 1) {
            return availableActions.iterator().next();
        }

        //This method is provided by the AbstractGame interface and used to track remaining computation time with shouldStopComputation()
        setTimers(l, timeUnit);

        // Set the utility values for end of round check in simulations
        oldUtilityPlayer0 = schnapsen.getUtilityValue(0);
        oldUtilityPlayer1 = schnapsen.getUtilityValue(1);

        List<PlayingCard> deckOfCards = generateFullDeck(board.getTrumpCard().getSuit());

        // Setting starting values for Imperfect Information Monte Carlo
        Map<SchnapsenAction, Double> value = new HashMap<>();
        Map<SchnapsenAction, Integer> visits = new HashMap<>();
        for (SchnapsenAction action : availableActions) {
            value.put(action, 0.0);
            visits.put(action, 0);
        }

        // track how often we sample = how many "worlds"
        int worldsSampled = 0;

        // let the algorithm sample till the time limit is reached
        while (!shouldStopComputation()) {

            // create a new perfect information board and game (sample world)
            SchnapsenBoard generatedBoard = generateMissingInformation(board, deckOfCards);
            Schnapsen sampleWorld = new Schnapsen(generatedBoard);

            // every move should be tried till reaching a game over or round end state
            for (SchnapsenAction m : availableActions) {
                if (shouldStopComputation()) break;

                //The simulation or playout of this action
                double v = finishedGameValue(sampleWorld, m);

                //We track each actions value
                value.put(m, value.get(m) + v);
                visits.put(m, visits.get(m) + 1);
            }

            worldsSampled++;
        }

        log._debugf("IIMC completed %d sampled worlds", worldsSampled);

        // select the best action based on their statistics
        log.debug("--- Action Statistics ---");
        SchnapsenAction bestAction = null;
        double bestAverageScore = -1.0;

        for (SchnapsenAction action : availableActions) {
            int actionVisits = visits.get(action);
            double score = value.get(action);
            //check for 0 division
            double avgScore = (actionVisits > 0) ? (score / actionVisits) : 0.0;

            log._debugf("Action: %-20s | Playouts: %6d | Expected Value: %5.2f",
                    action.toString(),
                    actionVisits,
                    avgScore);

            if (avgScore > bestAverageScore) {
                bestAverageScore = avgScore;
                bestAction = action;
            }
        }

        //As a fallback for not finding a suitable action, we choose the next possible action
        if (bestAction == null) {
            return availableActions.iterator().next();
        }

        log._debugf("--> CHOSEN ACTION: %s", bestAction.toString());
        return bestAction;
    }

    /**
     * Represents one possible playout (till end of game or end of round) for the action and game passed in the method
     * <p>
     * We use an epsilon greedy approach, where we play our heuristic action if the random number between 0.0 and 1.0 is higher than EPSILON_GREEDY.
     * Therefore, if EPSILON_GREEDY is 0.3 we choose the heuristic action about ~70% of the time.
     * @param schnapsen the current game state
     * @param schnapsenAction the action to be evaluated
     * @return a double containing the score at the end of the simulation
     */
    private double finishedGameValue(Schnapsen schnapsen, SchnapsenAction schnapsenAction) {

        Schnapsen playoutSchnapsen = (Schnapsen) schnapsen.doAction(schnapsenAction);

        while (!shouldStopComputation() && !playoutSchnapsen.isGameOver() && !isRoundOver(playoutSchnapsen)) {

            // Here we add our heuristic playout based on the epsilon greedy approach
            Set<SchnapsenAction> possibleActions = playoutSchnapsen.getPossibleActions();
            SchnapsenAction playoutAction;
            if(Math.random() < EPSILON_GREEDY) {
                // we select a random action
                playoutAction = Util.selectRandom(possibleActions);
            }
            else {
                // Here we let the algorithm choose the heuristical playout
                playoutAction = getHeuristicAction(playoutSchnapsen);
            }

            //We have to apply the action to the board on either choice
            playoutSchnapsen = (Schnapsen) playoutSchnapsen.doAction(playoutAction);

        }

        // return the simulations score
        return simulationScore(playoutSchnapsen);
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
