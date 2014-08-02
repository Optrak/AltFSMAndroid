package com.optrak.androidExperiment

import akka.actor.{ActorRef, Props}
import android.os.Bundle
import android.view.View.OnClickListener
import android.view.{LayoutInflater, View, ViewGroup}
import android.widget.{Button, CheckBox, TextView}
import com.optrak.androidExperiment.AndroidThere.{ReactorOps, ThereFragment, ThereActivity}
import com.optrak.experiment.Controller.ThereClazz
import com.optrak.experiment.LivingFSM._
import com.optrak.experiment.{AbstractReactor, WrappedFSMData, LivingFSM}
import macroid.FullDsl._
import macroid.Ui
import macroid.contrib.Layouts.{HorizontalLinearLayout, VerticalLinearLayout}

// scalaz imports
import scalaz._
import std.option._
import std.option.optionSyntax._
import std.list._
import syntax.traverse._

/**
 * Created by oscarvarto on 2014/07/22.
 */
class LivingActivity extends ThereActivity {
  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    val view = l[VerticalLinearLayout](
      f[LivingFragment].framed(Id.Living, Tag.Living)
    )
    setContentView(getUi(view))
  }
}

class LivingFragment extends ThereFragment {
  val name: String = LivingFSM.Name

  var livingStateTextView = slot[TextView]
  var dressedCheckBox = slot[CheckBox]
  var showeredCheckBox = slot[CheckBox]
  var friendWaitingCheckBox = slot[CheckBox]

  var eatingButton = slot[Button]
  var watchingTVButton = slot[Button]
  var startStudyingButton = slot[Button]
  var outOrComeBackButton = slot[Button] // also for comeBack
  var friendArrivesButton = slot[Button]

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = getUi {
    l[VerticalLinearLayout](
      w[TextView] <~ wire(livingStateTextView),
      l[HorizontalLinearLayout](
        w[TextView] <~ text("Dressed"),
        w[CheckBox] <~ wire(dressedCheckBox)
      ),
      l[HorizontalLinearLayout](
        w[TextView] <~ text("Showered"),
        w[CheckBox] <~ wire(showeredCheckBox)
      ),
      l[HorizontalLinearLayout](
        w[TextView] <~ text("FriendWaiting"),
        w[CheckBox] <~ wire(friendWaitingCheckBox)
      ),
      w[Button] <~ wire(eatingButton),
      w[Button] <~ wire(watchingTVButton),
      w[Button] <~ wire(startStudyingButton)
        <~ text(R.string.start_studying)
        <~ On.click { Ui(reactor ! StartStudying) },
      w[Button] <~ wire(outOrComeBackButton),
      w[Button] <~ wire(friendArrivesButton)
        <~ text(R.string.friend_arrives)
        <~ On.click { Ui(reactor ! FriendArrives) }
    )
  }

  def updateData(data: WrappedFSMData): Unit = {
    data.stateModel.data match {
      case livingData: LivingFSM.LivingData =>
        //debug(s"LivingFragment.updateData() called. $livingData received")
        val state = data.stateModel.state
        livingStateTextView.err(UserInterfaceNotReady) setText state.toString // show state in livingStateTextView

        val checkBoxOpts = List(dressedCheckBox, showeredCheckBox, friendWaitingCheckBox)
        // tick/untick checkBoxes according to livingData
        val checkBoxes: List[CheckBox] = checkBoxOpts.sequence[Option, CheckBox].err(UserInterfaceNotReady)
        val checks: List[Boolean] = List(livingData.dressed, livingData.showered, livingData.friendWaiting)
        (checkBoxes zip checks) foreach { case (cb, b) => cb setChecked b }

        val buttonOpts = List(eatingButton, watchingTVButton, outOrComeBackButton)
        val buttons: List[Button] = buttonOpts.sequenceU.err(UserInterfaceNotReady)
        val defaultLabels: List[Label] = List(
          R.string.start_eating,
          R.string.start_watching_tv,
          R.string.go_out
        )
        buttons match {
          case btns @ List(eatingBtn, watchingBtn, outOrComeBackBtn) =>
            val button2DefaultLabel: Map[Button, Label] = (btns zip defaultLabels).toMap
            val actualButton2Label = state match {
              case Eating => button2DefaultLabel.updated(eatingBtn, R.string.stop_eating)
              case WatchingTV => button2DefaultLabel.updated(watchingBtn, R.string.stop_watching_tv)
              case OutAndAbout => button2DefaultLabel.updated(outOrComeBackBtn, R.string.come_back)
              case _ => button2DefaultLabel
            }
            actualButton2Label foreach { case (btn, label) => btn setText label }

            val eatingMsg = if (actualButton2Label(eatingBtn) == R.string.start_eating) StartEating else StopEating
            eatingBtn.setOnClickListener(new OnClickListener {
              def onClick(v: View): Unit = reactor ! eatingMsg
            })

            val watchingMsg = if (actualButton2Label(watchingBtn) == R.string.start_watching_tv) StartWatchingTV else StopWatchingTV
            watchingBtn.setOnClickListener(new OnClickListener {
              def onClick(v: View): Unit = reactor ! watchingMsg
            })

            val outOrComeBackMsg = if (actualButton2Label(outOrComeBackBtn) == R.string.go_out) GoOut else ComeBack
            outOrComeBackBtn.setOnClickListener(new OnClickListener {
              def onClick(v: View): Unit = reactor ! outOrComeBackMsg
            })
          case _ => new Exception("Programming Error")
        }
      case _ => new Exception(WrongStateModelType)
    }
  }

  def createReactor: ActorRef = actorSystem.actorOf(LivingReactor.props(this, this.getClass))
}

object LivingReactor {
  def props(owner: LivingFragment, clazz: ThereClazz): Props = Props(new LivingReactor(owner, clazz, LivingFSM.Name))
}

class LivingReactor(owner: LivingFragment, clazz: ThereClazz, fsmName: String)
  extends AbstractReactor(owner, clazz, fsmName) with ReactorOps {
  def receive: Receive = baseReceive
}