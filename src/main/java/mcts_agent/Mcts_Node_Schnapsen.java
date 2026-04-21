package mcts_agent;

import game.Schnapsen;
import game.action.SchnapsenAction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Mcts_Node_Schnapsen {

    //Used to track the parentAction leading to this child
    private SchnapsenAction parentAction;
    //Used to get to the parentNode of the child
    private Mcts_Node_Schnapsen parentNode;
    //The state of the game
    private Schnapsen game;
    //Possible actions to take from this child's game state
    private List<SchnapsenAction> possibleActions;
    //A list of all children of this node
    private List<Mcts_Node_Schnapsen> childNodes;


    //These variables track the simulation score and the visitations on the node
    private int visitations;
    private double score;

    /**
     * A constructor for creating a new node.
     * The possibleActions for this node are checked and shuffled to not bias the selection of untried children
     * @param parentAction The action leading to this node
     * @param parentNode the parent of the node in the tree
     * @param game the games state of this node
     */
    public Mcts_Node_Schnapsen(SchnapsenAction parentAction, Mcts_Node_Schnapsen parentNode, Schnapsen game) {
        this.parentAction = parentAction;
        this.parentNode = parentNode;
        this.game = game;
        this.possibleActions = new ArrayList<>(game.getPossibleActions());
        Collections.shuffle(possibleActions);
        this.childNodes = new ArrayList<>();
    }

    /**
     * Checks if all possible actions from this nodes game state have been used by children
     * @return true if all actions are used in a child node
     */
    public boolean isCompletelyExpanded() {
        return possibleActions.isEmpty();
    }

    /**
     * This returns the number of visitations of the node
     * @return int representing the visitation number
     */
    public int getVisitations() {
        return visitations;
    }

    /**
     * Used to increase the visitation score of this node
     */
    public void incrementVisitations() {
        this.visitations++;
    }

    /**
     * Returns the nodes score
     * @return a double representing the scores of this node
     */
    public double getScore() {
        return score;
    }

    /**
     * Used to add score to the existing one of the node
     * @param score to be added to the nodes score
     */
    public void addScore(double score) {
        this.score += score;
    }

    /**
     * A list of all children of this node
     * @return a list of child nodes
     */
    public List<Mcts_Node_Schnapsen> getChildNodes() {
        return childNodes;
    }

    /**
     * The state of the game can be returned
     * @return a Schnapsen game state
     */
    public Schnapsen getGame() {
        return game;
    }

    /**
     * This method checks available actions on this node and chooses the first one. Then it creates a new node that is tracked as a child of this node
     * By removing it from the list, the node knows when it is fully expanded (== list is empty)
     * @return a newly created child node with this node as a parent
     */
    public Mcts_Node_Schnapsen getUntriedChild() {
        SchnapsenAction action = possibleActions.getFirst();
        possibleActions.remove(action);
        Schnapsen newGame = (Schnapsen) game.doAction(action);
        Mcts_Node_Schnapsen newChild = new Mcts_Node_Schnapsen(action, this, newGame);
        this.childNodes.add(newChild);
        return newChild;
    }

    /**
     * Returns the parent node of this node
     * @return a node that is the parent of this one
     */
    public Mcts_Node_Schnapsen getParentNode() {
        return parentNode;
    }

    /**
     * Returns the action that led to this node's game state
     * @return a SchnapsenAction that represents the action taken to get to this node from the parents game state
     */
    public SchnapsenAction getParentAction() {
        return parentAction;
    }
}
