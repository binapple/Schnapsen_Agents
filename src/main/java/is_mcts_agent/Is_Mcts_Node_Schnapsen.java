package is_mcts_agent;

import game.action.SchnapsenAction;

import java.util.ArrayList;
import java.util.List;

public class Is_Mcts_Node_Schnapsen {

    //Used to track the parentAction leading to this child
    private SchnapsenAction parentAction;
    //Used to get to the parent of this node
    private Is_Mcts_Node_Schnapsen parentNode;
    //A list of all children of this node
    private List<Is_Mcts_Node_Schnapsen> childNodes;

    //These variables are used for the backpropagation of the node, the track the score and the amount of visitations of this node
    private int visitations;
    private double score;

    //The availability counter is used in the ISMCTS selection formula
    private int availabilityCount;

    /**
     * This constructor creates a new node. When we create a new node its availability is already 1.
     * @param parentAction The action leading to this nodes state
     * @param parentNode The parent node in the tree
     */
    public Is_Mcts_Node_Schnapsen(SchnapsenAction parentAction, Is_Mcts_Node_Schnapsen parentNode) {
        this.parentAction = parentAction;
        this.parentNode = parentNode;
        this.childNodes = new ArrayList<>();
        //when creating a node it is automatically available
        this.availabilityCount = 1;
    }

    /**
     * We add a new child to this nodes list of children
     * @param childNode the node to be child of this node
     */
    public void addChild(Is_Mcts_Node_Schnapsen childNode) {
        this.childNodes.add(childNode);
    }

    /**
     * This method lets us search all the children's actions and return the one that matches the passed action
     * @param action the passed action that we want to find in the list of this node's children
     * @return a node which has the same parentAction as the passed one. Null if no child was found or action is null
     */
    public Is_Mcts_Node_Schnapsen findChildWithAction(SchnapsenAction action) {
        if (action == null) return null;
        for (Is_Mcts_Node_Schnapsen child : childNodes) {
            if(child.getParentAction().equals(action)) {
                return child;
            }
        }
        return null;
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
     * Used to increase the amount of visitations of this node
     */
    public void incrementAvailabilityCount() {
        this.availabilityCount++;
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
    public List<Is_Mcts_Node_Schnapsen> getChildNodes() {
        return childNodes;
    }

    /**
     * Returns the parent node of this node
     * @return a node that is the parent of this one
     */
    public Is_Mcts_Node_Schnapsen getParentNode() {
        return parentNode;
    }

    /**
     * This method sets the passed node as this nodes parent node.
     * This method is mainly used to reset the parents node to null in case this node becomes the new root node
     * @param parentNode the node to be set as parent of this node
     */
    public void setParentNode(Is_Mcts_Node_Schnapsen parentNode) {
        this.parentNode = parentNode;
    }

    /**
     * Returns the action that led to this node's game state
     * @return a SchnapsenAction that represents the action taken to get to this node from the parents game state
     */
    public SchnapsenAction getParentAction() {
        return parentAction;
    }

    /**
     * Returns the amount that this node has been available
     * @return int representing the availability count of this node
     */
    public int getAvailabilityCount() {
        return availabilityCount;
    }
}
