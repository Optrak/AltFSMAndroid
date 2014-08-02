package com.optrak

import java.util.concurrent.{LinkedBlockingQueue, ThreadPoolExecutor, TimeUnit}

import android.widget.Button
import macroid.FullDsl._
import macroid._

import scala.concurrent.ExecutionContext

/**
 * Created by oscarvarto on 2014/07/28.
 */
package object androidExperiment {
  type JList[A] = java.util.List[A]
  type Label = Int

  val TAG = "Debug"

  val (corePoolSize, maximumPoolSize, keepAliveTime) = (30, 30, 100)
  val workQueue = new LinkedBlockingQueue[Runnable]
  // Execution context for futures below
  implicit val exec = ExecutionContext.fromExecutor(
    new ThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveTime, TimeUnit.SECONDS, workQueue)
  )

  val UserInterfaceNotReady = "User interface must exist before calling updateUserInterface"
  val WrongStateModelType = "Programmer error(s): Wrong StateModel[_, _]"

  def bombTweak(AAName: String): Tweak[Button] = On.click {
    Ui(throw new Exception("Bomb in " + AAName))
  }
}
