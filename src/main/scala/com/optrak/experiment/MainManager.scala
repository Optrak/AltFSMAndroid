package com.optrak.experiment

import akka.actor.{ActorRef, ActorSystem}
import com.optrak.experiment.Controller._
import scalaz.syntax.std.option._

/**
 * Created by oscarvarto on 2014/06/12.
 */
trait MainManager {
  def actorSys: ActorSystem
  def initiator: ActorRef
  def hfsm: HFSM
  def initialData: WrappedHFSMData

  val controller = actorSys.actorOf(Controller.props(initiator), Controller.Name)
  val fsmProcessor = actorSys.actorOf(FSMProcessor.props(controller, hfsm, initialData), FSMProcessor.Name)
}


