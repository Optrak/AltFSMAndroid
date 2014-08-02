package com.optrak.androidExperiment

import akka.actor.{PoisonPill, ActorSystem}
import android.app.{Activity, Fragment}
import android.content.Intent
import android.os.Bundle
import android.util.Log
import ch.qos.logback.classic.android.BasicLogcatConfigurator
import com.optrak.experiment.Controller.ThereClazz
import com.optrak.experiment._
import macroid._
import grizzled.slf4j.Logging

/**
 * Created by oscarvarto on 2014/07/21.
 */
object AndroidThere {

  trait ThereActivity extends Activity with Contexts[Activity] with IdGeneration with Logging {
    BasicLogcatConfigurator.configureDefaultContext()
    //override def onBackPressed() {}
  }

  trait ThereFragment extends Fragment with AbstractFragment with Contexts[Fragment] {
    BasicLogcatConfigurator.configureDefaultContext()

    lazy val act: Activity = implicitly[ActivityContext].get

    def spawnActivity(clazz: ThereClazz): Unit = Ui {
      val i = new Intent(act, clazz)
      act.startActivity(i)
    }.run

    def actorSystem: ActorSystem = ActorSystemManager.actorSys

    def stop(): Unit = Ui {
      act.finish()
    }.run

    override def onCreate(savedInstanceState: Bundle): Unit = {
      reactor
      //Log.d(TAG, s"Fragment $this and $reactor CREATED")
      super.onCreate(savedInstanceState)
    }

    override def onDestroy(): Unit = {
      reactor ! PoisonPill
      //Log.d(TAG, s"Fragment $this and $reactor DESTROYED")
      super.onDestroy()
    }
  }

  trait ReactorOps {
    self: AbstractReactor =>
    def doOnUIThread(f: () => Unit): Unit = Ui {
      f()
    }.run

    def initialising: Receive = baseInitialising
  }

}
