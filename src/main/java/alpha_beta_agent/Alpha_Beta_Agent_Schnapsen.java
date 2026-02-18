package alpha_beta_agent;

import at.ac.tuwien.ifs.sge.agent.AbstractGameAgent;
import at.ac.tuwien.ifs.sge.agent.GameAgent;
import at.ac.tuwien.ifs.sge.engine.Logger;
import game.Schnapsen;
import game.action.SchnapsenAction;
import game.board.PlayingCard;
import game.board.SchnapsenBoard;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class Alpha_Beta_Agent_Schnapsen extends AbstractGameAgent<Schnapsen, SchnapsenAction> implements GameAgent<Schnapsen, SchnapsenAction> {

    private int maxDepth;

    public Alpha_Beta_Agent_Schnapsen() {
    }

    public Alpha_Beta_Agent_Schnapsen(Logger log) {
        super(log);
    }



    @Override
    public SchnapsenAction computeNextAction(Schnapsen schnapsen, long l, TimeUnit timeUnit) {
        SchnapsenBoard board = schnapsen.getBoard();
        Set<SchnapsenAction> availableActions = schnapsen.getPossibleActions();

        //single choice actions can be returned immediately
        if(availableActions.size() == 1){
            return availableActions.iterator().next();
        }

        SchnapsenBoard generatedBoard = generateMissingInformation(board);
        Schnapsen generatedSchnapsen = new Schnapsen(generatedBoard);

        //Simulation only makes sense for the current round, therefore we give maximum possible number of actions left in the round as maximum depth
        SchnapsenBoard currentBoard = generatedSchnapsen.getBoard();

        maxDepth = 0;

        //all the players cards are an action
        if(generatedSchnapsen.getCurrentPlayer() == 0)
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

        setTimers(l,  timeUnit);

        double bestActionValue = Double.MIN_VALUE;
        SchnapsenAction bestAction = null;

        double alpha = Double.MIN_VALUE;
        double beta = Double.MAX_VALUE;

        for(SchnapsenAction action : availableActions)
        {
            Schnapsen afterAction = (Schnapsen) generatedSchnapsen.doAction(action);
            double actionValue = alphaBeta_min_max(afterAction, 1, alpha, beta);

            if(actionValue > bestActionValue)
            {
                bestActionValue = actionValue;
                bestAction = action;
            }
            alpha = Math.max(alpha, bestActionValue);

        }

        if(bestAction != null)
        {
            return bestAction;
        } else {
            return availableActions.iterator().next();
        }
    }

    private double alphaBeta_min_max(Schnapsen currentSchnapsen, int currentDepth, double alpha, double beta){

        if(currentSchnapsen.isGameOver() || currentDepth >= maxDepth || shouldStopComputation())
        {
            return currentSchnapsen.getUtilityValue(playerId);
        }

        Set<SchnapsenAction> possibleActions = currentSchnapsen.getPossibleActions();

        if(playerId == currentSchnapsen.getCurrentPlayer())
        {
            double maximumValue = Double.MIN_VALUE;
            for(SchnapsenAction action : possibleActions){
                Schnapsen afterAction = (Schnapsen) currentSchnapsen.doAction(action);
                double actionValue = alphaBeta_min_max(afterAction, currentDepth+1, alpha, beta);
                maximumValue = Math.max(maximumValue, actionValue);
                alpha = Math.max(alpha, maximumValue);
                if (beta <= alpha) {
                    break;
                }
            }
            return maximumValue;
        } else {
            double minValue = Double.MAX_VALUE;
            for(SchnapsenAction action : possibleActions){
                Schnapsen afterAction = (Schnapsen) currentSchnapsen.doAction(action);
                double actionValue = alphaBeta_min_max(afterAction, currentDepth+1, alpha, beta);
                minValue = Math.min(minValue, actionValue);
                beta = Math.min(beta, minValue);
                if (beta <= alpha) {
                    break;
                }
            }
            return minValue;
        }

    }

    /**
     * This method takes all available information of the given board and randomizes the unknown cards into the drafting pile and opposing players hand
     * @param board the actual game board with hidden information
     * @return a new deep copied board with the given information and randomized cards for all unknown cards in the pile and opposing players hand
     */
    private SchnapsenBoard generateMissingInformation(SchnapsenBoard board) {

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

        //set the isTrump() field on newly created cards
        SchnapsenBoard.cardSuits trumpSuit = trumpCard.getSuit();
        for (PlayingCard card : unknownCards) {
            if( trumpSuit == card.getSuit()) {
                card.setIsTrumpSuit(true);
            }
        }

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
        // (if not, check if they are in the opposing players hand == not in my hand)
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
        for(PlayingCard playerCard: playerCards) {
            PlayingCard foundCard = findCardInPile(unknownCards, playerCard);
            unknownCards.remove(foundCard);
        }

        //Remove trumpCard
        PlayingCard foundTrumpCard = findCardInPile(unknownCards, trumpCard);
        unknownCards.remove(foundTrumpCard);

        //Remove leadingCard
        if(leadingCard != null) {
            PlayingCard foundLeadingCard = findCardInPile(unknownCards, leadingCard);
            unknownCards.remove(foundLeadingCard);
        }

        //Remove all of otherPlayers cards
        for(PlayingCard otherPlayerCard: otherPlayersCards){
            PlayingCard foundCard = findCardInPile(unknownCards, otherPlayerCard);
            unknownCards.remove(foundCard);
        }

        //Remove already played cards
        for(PlayingCard playedCard: trickCards) {
            PlayingCard foundCard = findCardInPile(unknownCards, playedCard);
            unknownCards.remove(foundCard);
        }


        //Fill up all the hidden card-slots with random available cards

        //shuffle the unkown cards
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
            playingCardPile.add(unknownCards.pop());
        }

        /*//add trumpCard as last if pile was not empty yet
        if(cardsLeftInPile > 0) {
            playingCardPile.addLast(trumpCard);
        } else {
            //add trump card to opponent hand if pile is empty, it is not in our hand and when it has not been played yet
            if(!playerCards.contains(trumpCard)) {
                if(!trickCards.contains(trumpCard)) {
                    if(leadingCard == null) {
                        otherPlayersCards.add(trumpCard);
                    }
                }
            }
        }*/

        // Add trumpCard as last if pile was not empty yet
        if(cardsLeftInPile > 0) {
            playingCardPile.addLast(trumpCard);
        } else {

            // 1. Check if we (the agent) hold it
            boolean inMyHand = playerCards.contains(trumpCard);

            // 2. Check if it was played in a previous trick
            boolean inTricks = trickCards.contains(trumpCard);

            // 3. Check if it is currently on the table (Leading Card)
            // (Use .equals to be safe!)
            boolean isLeading = (leadingCard != null && leadingCard.equals(trumpCard));

            // 4. CRITICAL: Check if we already added it to opponent's hand
            // (e.g., via the "Old Trump" exchange logic earlier)
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

    private PlayingCard findCardInPile(List<PlayingCard> pile, PlayingCard targetCard){
        if(targetCard.getCardName().equals(SchnapsenBoard.cardNames.PlaceHolder)) {
            return new PlayingCard(SchnapsenBoard.cardSuits.SPADES, SchnapsenBoard.cardNames.PlaceHolder,0);
        }
        return pile.stream().filter(p -> p.equals(targetCard)).findFirst().orElse(null);
    }

}
