package com.optrak

import java.util.concurrent.{TimeUnit, ThreadPoolExecutor, LinkedBlockingQueue}

import com.optrak.experiment.Controller._
import com.optrak.opkfsm.OpkFSM.{Msg, StateModel}
import org.joda.time.DateTime

import scala.concurrent.ExecutionContext
import scalaz.Validation

/**
 * Created by oscarvarto on 2014/07/15.
 */
package object experiment {
  type JList[A] = java.util.List[A]
  val TAG = "Debug"

  val (corePoolSize, maximumPoolSize, keepAliveTime) = (30, 30, 100)
  val workQueue = new LinkedBlockingQueue[Runnable]
  // Execution context for futures below
  implicit val exec = ExecutionContext.fromExecutor(
    new ThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveTime, TimeUnit.SECONDS, workQueue)
  )

  val UserInterfaceNotReady = "User interface must exist before calling updateUserInterface"


  /**
   * WrappedFSMData allows us to be independent of the internal details of the individual state models until we want to
   * unwrap them. Effectively it means we are going to wrap the StateModel details so they are not known to
   * FSMProcessor, Controller or ActivityActor and are only unwrapped in the data specific Reactor
   * This eliminates a lot of type [S, D] stuff and also makes Controller and FSMProcessor completely independent
   * of the underlying fsm deployed - so not changes needed if we choose to use a different fsm
   */
  trait WrappedFSMData {
    /**
     * @return the clazz that will be passed to "Android" to create an instance of the fragment or whatever
     */
    def thereClazz: ThereClazz

    /**
     * name used for labelling activities
     * @return
     */
    def name: String

    /**
     * underlying state model
     * @return
     */
    def stateModel: StateModel[_, _]
  }

  object WrappedFSMData {
    val Empty = new WrappedFSMData {
      def thereClazz: ThereClazz = ???
      def stateModel: StateModel[_, _] = ???
      def data: Any = ???
      def name: String = ???
    }
  }
  trait PushPop
  case class Push(model: WrappedFSMData) extends PushPop
  case object Pop extends PushPop

  trait WrappedHFSMData {
    def subs: Vector[WrappedFSMData]
    def pushPop: List[PushPop]
  }

  trait HFSM {
    def hProcess(msg: Msg, wrappedData: WrappedHFSMData): Validation[(Msg, StateModel[_, _]), WrappedHFSMData]
  }
}
