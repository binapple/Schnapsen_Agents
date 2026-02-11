package mcts_agent;

import game.Schnapsen;
import game.action.SchnapsenAction;
import game.board.SchnapsenBoard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Mcts_Node_Schnapsen {

    private SchnapsenAction parentAction;
    private Mcts_Node_Schnapsen parentNode;
    private Schnapsen game;
    private List<SchnapsenAction> possibleActions;
    private List<Mcts_Node_Schnapsen> childNodes;

    private int visitations;
    private double score;

    public Mcts_Node_Schnapsen(SchnapsenAction parentAction, Mcts_Node_Schnapsen parentNode, Schnapsen game) {
        this.parentAction = parentAction;
        this.parentNode = parentNode;
        this.game = game;
        this.possibleActions = new ArrayList<>(game.getPossibleActions());
        Collections.shuffle(possibleActions);
        this.childNodes = new ArrayList<>();
    }

    public boolean isCompletelyExpanded() {
        return possibleActions.isEmpty();
    }

    public int getVisitations() {
        return visitations;
    }

    public void incrementVisistations() {
        this.visitations++;
    }

    public double getScore() {
        return score;
    }

    public void addScore(double score) {
        this.score += score;
    }

    public List<Mcts_Node_Schnapsen> getChildNodes() {
        return childNodes;
    }

    public Schnapsen getGame() {
        return game;
    }

    public Mcts_Node_Schnapsen getUntriedChild() {
        SchnapsenAction action = possibleActions.getFirst();
        possibleActions.remove(action);
        Schnapsen newGame = (Schnapsen) game.doAction(action);
        Mcts_Node_Schnapsen newChild = new Mcts_Node_Schnapsen(action, this, newGame);
        this.childNodes.add(newChild);
        return newChild;
    }

    public Mcts_Node_Schnapsen getParentNode() {
        return parentNode;
    }

    public SchnapsenAction getParentAction() {
        return parentAction;
    }
}
