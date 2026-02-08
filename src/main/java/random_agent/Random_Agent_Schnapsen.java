package random_agent;

import at.ac.tuwien.ifs.sge.agent.AbstractGameAgent;
import at.ac.tuwien.ifs.sge.agent.GameAgent;
import at.ac.tuwien.ifs.sge.engine.Logger;
import game.Schnapsen;
import game.action.SchnapsenAction;

import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class Random_Agent_Schnapsen extends AbstractGameAgent<Schnapsen, SchnapsenAction> implements GameAgent<Schnapsen, SchnapsenAction> {

    public Random_Agent_Schnapsen(Logger log) {
        super(log);
    }

    /**
     * This method will be called by the engine everytime the agent has its turn.
     * In this method the agent looks at all the possible actions to take and picks a random one of them to return.
     * @param schnapsen the games state as given by the engine (may include hidden information)
     * @param computationTime the maximum available time the agent is allowed to take to think about its next action
     * @param timeUnit the unit in which the computationTime is measured
     * @return a random SchnapsenAction chosen from the agents available ones
     */
    @Override
    public SchnapsenAction computeNextAction(Schnapsen schnapsen, long computationTime, TimeUnit timeUnit) {
        Set<SchnapsenAction> actions = schnapsen.getPossibleActions();
        Object[] actionArray = actions.toArray();
        int actionCount = actionArray.length;
        int pickedActionId = new Random().nextInt(actionCount);
        return (SchnapsenAction) actionArray[pickedActionId];
    }
}
