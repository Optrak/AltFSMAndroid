package com.optrak.androidExperiment

import akka.actor._
import android.content.Context
import com.optrak.experiment.StudentFSM.{SInitial, StudentStateData}
import com.optrak.experiment.{HFSM, MainManager, WrappedHFSMData}

/**
 * Created by oscarvarto on 2014/07/22.
 */
object ActorSystemManager {

  System.setProperty("sun.arch.data.model", "32")
  System.setProperty("leveldb.mmap", "false")
  val ActorSystemName = "Experiment"

  val actorSys: ActorSystem = ActorSystem(ActorSystemName)

  val hfsm: MyHFSM = new MyHFSM()
  val initiator: ActorRef =
    actorSys.actorOf(Props(new ReceiveInitialReactor(hfsm, initialData)), ReceiveInitialReactor.Name)
  var manager: Option[MManager] = None

  def initialData: WrappedHFSMData = hfsm.wrap(hfsm.model(SInitial, StudentStateData()))

  var mAppContext: Option[Context] = None

  def apply(ctx: Context): this.type = {
    mAppContext = Some(ctx.getApplicationContext)
    this
  }
}

class MManager(val actorSys: ActorSystem,
               val initiator: ActorRef,
               val hfsm: HFSM,
               val initialData: WrappedHFSMData) extends MainManager