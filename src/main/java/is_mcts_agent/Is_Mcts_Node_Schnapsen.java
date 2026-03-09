package is_mcts_agent;

import game.Schnapsen;
import game.action.SchnapsenAction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Is_Mcts_Node_Schnapsen {

    private SchnapsenAction parentAction;
    private Is_Mcts_Node_Schnapsen parentNode;
    private List<Is_Mcts_Node_Schnapsen> childNodes;

    private int visitations;
    private double score;

    private int availabilityCount;

    public Is_Mcts_Node_Schnapsen(SchnapsenAction parentAction, Is_Mcts_Node_Schnapsen parentNode) {
        this.parentAction = parentAction;
        this.parentNode = parentNode;
        this.childNodes = new ArrayList<>();
        //when creating a node it is automatically available
        this.availabilityCount = 1;
    }

    public void addChild(Is_Mcts_Node_Schnapsen childNode) {
        this.childNodes.add(childNode);
    }

    public Is_Mcts_Node_Schnapsen findChildWithAction(SchnapsenAction action) {
        if (action == null) return null;
        for (Is_Mcts_Node_Schnapsen child : childNodes) {
            if(child.getParentAction().equals(action)) {
                return child;
            }
        }
        return null;
    }

    public int getVisitations() {
        return visitations;
    }

    public void incrementVisistations() {
        this.visitations++;
    }

    public void incrementAvailabilityCount() {
        this.availabilityCount++;
    }

    public double getScore() {
        return score;
    }

    public void addScore(double score) {
        this.score += score;
    }

    public List<Is_Mcts_Node_Schnapsen> getChildNodes() {
        return childNodes;
    }


    public Is_Mcts_Node_Schnapsen getParentNode() {
        return parentNode;
    }

    public void setParentNode(Is_Mcts_Node_Schnapsen parentNode) {
        this.parentNode = parentNode;
    }

    public SchnapsenAction getParentAction() {
        return parentAction;
    }

    public int getAvailabilityCount() {
        return availabilityCount;
    }
}
