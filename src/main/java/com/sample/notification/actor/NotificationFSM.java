package com.sample.notification.actor;

import akka.actor.ReceiveTimeout;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import static com.sample.notification.actor.NotificationFSMMessages.*;


/**
 * Created by rpatel on 7/17/14.
 */
@Component("NotificationActor")
@Scope("prototype")
public class NotificationFSM extends NotificationFSMBase{

    private final LoggingAdapter log = Logging.getLogger(getContext().system(), this);
    public NotificationFSM() {}

    @Override
    public void onReceive(Object object) {

        log.info("message in FSM {}, when state is {}", object, getState().toString());

        if (object instanceof UnRegisterTarget) {
            if(isTargetAvailable() && getTarget().equals(getSender())){
                setTarget(null);
                if(getState() == State.WAITING_FOR_DATA){
                    setState(State.WAITING);
                }
            }
        } else {

            if (object instanceof SetTarget) {
                setTarget(((SetTarget) object).ref);
            } else if (object instanceof Queue) {
                enqueue(((Queue) object).message);
            }

            // action based on state
            if (getState() == State.START) {
                handlStart(object);
            } else if (getState() == State.WAITING) {
                handleWaiting(object);
            } else if (getState() == State.WAITING_FOR_DATA) {
                handleWaitingForData(object);
            } else if (getState() == State.WAITING_FOR_TARGET) {
                handleWaitingForTarget(object);
            }
        }

        log.info("new state {}", getState().toString());
    }



    @Override
    public void unhandled(Object object){
        log.warning("received unknown message {} in state {}", object, getState());
        super.unhandled(object);

    }

    public void handlStart(Object object) {
        if (object instanceof SetTarget) {
            // todo : db call and set state to waiting
            setState(State.WAITING_FOR_DATA);
        } else {
            unhandled(object);
        }

    }

    public void handleWaiting(Object object){
        if (object instanceof SetTarget) {
            setState(State.WAITING_FOR_DATA);
        } else if (object instanceof Queue) {
            setState(State.WAITING_FOR_TARGET);
        } else if (object instanceof ReceiveTimeout){
            getContext().stop(getSelf());
        } else {
            unhandled(object);
        }
    }

    public void handleWaitingForTarget(Object object){
        if (object instanceof SetTarget) {
            if (isMessageAvailable()) {
                sendDataToTarget();
                setState(State.WAITING);
            } else {
                setState(State.WAITING_FOR_DATA);
            }
        } else {
            unhandled(object);
        }
    }

    public void handleWaitingForData(Object object){
        if (object instanceof Queue) {
            if (isTargetAvailable()) {
                sendDataToTarget();
                setState(State.WAITING);
            }else {
                setState(State.WAITING_FOR_TARGET);
            }
        } else {
            unhandled(object);
        }
    }

    private void sendDataToTarget() {
        getTarget().tell(new Batch(drainQueue()), getSelf());
        setTarget(null);

    }

}