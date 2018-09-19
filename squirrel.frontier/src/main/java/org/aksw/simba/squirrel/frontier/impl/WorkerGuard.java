package org.aksw.simba.squirrel.frontier.impl;

import org.aksw.simba.squirrel.components.FrontierComponent;
import org.aksw.simba.squirrel.data.uri.CrawleableUri;
import org.aksw.simba.squirrel.worker.AliveMessage;
import org.aksw.simba.squirrel.worker.WorkerInfo;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * This class checks whether some {@link org.aksw.simba.squirrel.worker.Worker} has died and propagates the
 * information to the {@link FrontierComponent}.
 *
 * @author Philip Frerk
 */
public class WorkerGuard {

    /**
     * A map from {@link org.aksw.simba.squirrel.worker.Worker} id to {@link WorkerInfo} containing information about the
     * {@link org.aksw.simba.squirrel.worker.Worker}.
     */
    private Map<String, WorkerInfo> mapWorkerInfo = Collections.synchronizedMap(new HashMap<>());

    /**
     * After this period of time (in seconds), a worker is considered to be dead if he has not sent
     * an {@link AliveMessage} since.
     */
    public final static long TIME_WORKER_DEAD = 10;


    /**
     * Counting the number of workers that already died.
     */
    private int numberOfDeadWorkers = 0;

    /**
     * The timer to check for dead or alive workers.
     */
    private final Timer timer = new Timer();


    /**
     * Create an object of this class and provide an instance of {@link FrontierComponent} that it can contact.
     *
     * @param frontierComponent The instance of {@link FrontierComponent}.
     */
    public WorkerGuard(FrontierComponent frontierComponent) {
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                List<String> lstIdsToBeRemoved = new ArrayList<>();
                for (String idWorker : mapWorkerInfo.keySet()) {

                    if (mapWorkerInfo.get(idWorker).getDateLastAlive() == null) {
                        continue;
                    }

                    boolean currentWorkerSendsAliveMessages = mapWorkerInfo.get(idWorker).workerSendsAliveMessages();
                    // if a worker does not alive messages in general he will not be removed
                    if (currentWorkerSendsAliveMessages) {
                        long duration = new Date().getTime() - mapWorkerInfo.get(idWorker).getDateLastAlive().getTime();
                        if (TimeUnit.MILLISECONDS.toSeconds(duration) > TIME_WORKER_DEAD + 10) {
                            // worker is dead
                            lstIdsToBeRemoved.add(idWorker);
                        }
                    }
                }

                synchronized (this) {
                    lstIdsToBeRemoved.forEach(id -> {
                        frontierComponent.informFrontierAboutDeadWorker(id, mapWorkerInfo.get(id).getUrisCrawling());
                        mapWorkerInfo.remove(id);
                        numberOfDeadWorkers++;
                    });

                }
            }
        }, 0, TimeUnit.SECONDS.toMillis(TIME_WORKER_DEAD) / 2);
    }

    /**
     * Put a new date for the worker identified by the given id.
     *
     * @param idOfWorker the given id.
     */
    public void putNewTimestamp(String idOfWorker) {
        WorkerInfo workerInfo;
        if (mapWorkerInfo.containsKey(idOfWorker)) {
            workerInfo = mapWorkerInfo.get(idOfWorker);
            workerInfo.setDateLastAlive(new Date());
        } else {
            workerInfo = new WorkerInfo(true, new ArrayList<>(), new Date());
        }
        mapWorkerInfo.put(idOfWorker, workerInfo);
    }

    /**
     * Put the given uris for the given worker so that he can crawl them.
     *
     * @param idOfWorker The id of the worker for which to put the uris.
     * @param lstUris    The uris to put.
     */
    public void putUrisForWorker(String idOfWorker, boolean workerSendsAliveMessages, List<CrawleableUri> lstUris) {
        WorkerInfo workerInfo;
        if (mapWorkerInfo.containsKey(idOfWorker)) {
            workerInfo = mapWorkerInfo.get(idOfWorker);
            workerInfo.getUrisCrawling().addAll(lstUris);
        } else {
            workerInfo = new WorkerInfo(workerSendsAliveMessages, lstUris, new Date());
        }
        mapWorkerInfo.put(idOfWorker, workerInfo);
    }

    /**
     * Remove the given uris for the given worker.
     *
     * @param idOfWorker      The id of the worker.
     * @param lstUrisToRemove The uris to be removed.
     */
    public void removeUrisForWorker(String idOfWorker, List<CrawleableUri> lstUrisToRemove) {
        if (!mapWorkerInfo.containsKey(idOfWorker)) {
            throw new IllegalArgumentException("Illegal call. Worker with id " + idOfWorker + " should be contained in the info map.");
        }
        if (mapWorkerInfo.get(idOfWorker).getUrisCrawling() == null || mapWorkerInfo.get(idOfWorker).getUrisCrawling().size() == 0) {
            return;
        }
        mapWorkerInfo.get(idOfWorker).getUrisCrawling().removeAll(lstUrisToRemove);
    }

    /**
     * Make the Guard stop working.
     */
    public void shutdown() {
        timer.cancel();
    }

    public Map<String, WorkerInfo> getMapWorkerInfo() {
        return mapWorkerInfo;
    }

    /**
     * Getter for the number of running workers.
     *
     * @return the number of running workers.
     */
    public int getNumberOfLiveWorkers() {
        return mapWorkerInfo.size();
    }

    /**
     * Getter for {@link #numberOfDeadWorkers}.
     *
     * @return {@link #numberOfDeadWorkers}..
     */
    public int getNumberOfDeadWorker() {
        return numberOfDeadWorkers;
    }


}
