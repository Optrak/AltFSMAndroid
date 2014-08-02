package com.optrak.androidExperiment

import akka.actor.{Actor, PoisonPill}
import ch.qos.logback.classic.android.BasicLogcatConfigurator
import com.optrak.androidExperiment.InitialFragment.AuthSuccess
import com.optrak.experiment.StudentFSM.Logon
import com.optrak.experiment.{HFSM, WrappedHFSMData}
//import grizzled.slf4j.Logging

/**
 * Created by oscarvarto on 2014/07/28.
 */
object ReceiveInitialReactor {
  val Name = "ReceiveInitialReactor"
}

class ReceiveInitialReactor(hfsm: HFSM, initialData: WrappedHFSMData) extends Actor {
  def receive = {
    case AuthSuccess(initialReactor, username, age) =>
      val man = new MManager(context.system, initialReactor, hfsm, initialData)
      ActorSystemManager.manager = Some(man)
      man.fsmProcessor ! Logon(username, age)
  }
}