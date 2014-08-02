package com.optrak.androidExperiment

import akka.actor.{Actor, ActorRef, Props}
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.{LayoutInflater, View, ViewGroup}
import android.widget.{Toast, Button, EditText}
import ch.qos.logback.classic.android.BasicLogcatConfigurator
import com.optrak.androidExperiment.AndroidThere.{ThereFragment, ThereActivity}
import com.optrak.experiment.AbstractFragment.CreateThereActivity
import com.optrak.experiment.WrappedFSMData
import com.optrak.opkfsm.OpkFSM.Msg
import macroid.FullDsl._
import macroid._
import macroid.contrib.Layouts.VerticalLinearLayout
import net.danlew.android.joda.JodaTimeAndroid

import scala.concurrent.Future
import grizzled.slf4j.Logging

import scalaz.Validation
import scalaz.std.string.stringSyntax._

/**
 * Created by oscarvarto on 2014/07/21.
 */
class InitialActivity extends ThereActivity {
  override def onCreate(savedInstanceState: Bundle): Unit = {
    // ActorSystemManager(this)
    super.onCreate(savedInstanceState)
    JodaTimeAndroid.init(this)
    val view = l[VerticalLinearLayout](
      f[InitialFragment].framed(Id.Initial, Tag.Initial)
    )
    setContentView(getUi(view))
  }
}

object InitialFragment {
  val Name = "Initial"
  val WrongCredentials = "Wrong Credentials"
  val AuthenticationFailure = "Authentication Failure"

  case class Authenticate(username: String, age: Int) extends Msg

  case class AuthSuccess(initialReactor: ActorRef, name: String, age: Int) extends Msg
}

import com.optrak.androidExperiment.InitialFragment._
import scalaz.std.option.optionSyntax._

class InitialFragment extends ThereFragment {
  val name: String = "Initial" //InitialFragment.Name

  var userNameET = slot[EditText]
  var ageET = slot[EditText]
  var logonBtn = slot[Button]

  lazy val userNameTweak = Tweak[EditText] { et =>
    et.setHint(R.string.user_name_hint)
    et.setInputType(InputType.TYPE_CLASS_TEXT)
  }
  lazy val ageTweak = Tweak[EditText] { et =>
    et.setHint(R.string.user_age_hint)
    et.setInputType(InputType.TYPE_CLASS_NUMBER)
  }
  lazy val logonTweak = On.click {
    Ui {
      val userName: String = userNameET.err(UserInterfaceNotReady).getText.toString
      val ageOpt = ageET.err(UserInterfaceNotReady).getText.toString.parseInt.toOption
      ageOpt.cata(
        some = age => authenticate(userName, age),
        none = (toast(getString(R.string.age_error_input)) <~ fry).run
      )
    }
  }

  def updateData(data: WrappedFSMData): Unit = ()

  def createReactor: ActorRef = actorSystem.actorOf(InitialReactor.props(this), name)

  // User will introduce his credentials and wait for authentication success/failure
  // This method could delegate authentication to InitialReactor to be able to do
  // network communication in background thread (the reactor).
  def authenticate(username: String, age: Int): Unit = {
    reactor ! Authenticate(username, age)
  }

  def showWrongCredentials(): Ui[Toast] = toast(WrongCredentials) <~ fry

  def showAuthFailure(): Ui[Toast] = toast(AuthenticationFailure) <~ fry

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = getUi {
    l[VerticalLinearLayout](
      w[EditText] <~ wire(userNameET) <~ userNameTweak,
      w[EditText] <~ wire(ageET) <~ ageTweak,
      w[Button] <~ wire(logonBtn) <~ text("Logon") <~ logonTweak
    )
  }
}

object InitialReactor {
  def props(owner: InitialFragment): Props = Props(new InitialReactor(owner))
}

class InitialReactor(owner: InitialFragment) extends Actor with Logging {
  def receive: Receive = {
    case auth@Authenticate(username, age) =>
      val authFuture: Future[Boolean] = doAuth(auth)
      authFuture onSuccess {
        case true =>
          val receiveInitialReactor = context.actorSelection(s"/user/${ReceiveInitialReactor.Name}")
          receiveInitialReactor ! AuthSuccess(self, username, age)
        case false =>
          debug(s"Should see: $WrongCredentials")
          owner.showWrongCredentials().run
      }
      authFuture onFailure {
        case reason: Throwable =>
          debug(s"Should see: $AuthenticationFailure")
          owner.showAuthFailure().run
      }
    case cta @ CreateThereActivity(clazz, _) =>
      debug(s"$self received $cta")
      Ui {
        val i = new Intent(owner.getActivity, clazz)
        owner.startActivity(i)
        owner.getActivity.finish()
      }.run
  }

  def doAuth(auth: Authenticate): Future[Boolean] = auth match {
    case Authenticate("o", 3) => Future.successful(true) // Auth success
    case Authenticate("o", _) => Future.successful(false) // Wrong credentials
    case _ => Future.failed(new Exception("Unknown problem")) // Unknown problem, no auth done
  }
}