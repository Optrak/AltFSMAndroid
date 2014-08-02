package com.optrak.androidExperiment

import akka.actor.{ActorRef, Props}
import android.os.Bundle
import android.view.View.OnClickListener
import android.view.{View, ViewGroup, LayoutInflater}
import android.widget.{Button, CheckBox, TextView}
import com.optrak.androidExperiment.AndroidThere.{ReactorOps, ThereFragment, ThereActivity}
import com.optrak.experiment.Controller.ThereClazz
import com.optrak.experiment.{AbstractReactor, WrappedFSMData, GettingReadyFSM}
import macroid._
import macroid.FullDsl._
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
class GettingReadyActivity extends ThereActivity {
  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    val view = l[VerticalLinearLayout] (
      f[GettingReadyFragment].framed(Id.GettingReady, Tag.GettingReady)
    )
    setContentView(getUi(view))
  }
}

class GettingReadyFragment extends ThereFragment {
  val name: String = GettingReadyFSM.Name

  var gettingReadyStateTextView = slot[TextView]
  var dressedCheckBox = slot[CheckBox]
  var showeredCheckBox = slot[CheckBox]

  var dressingButton = slot[Button]
  var showeringButton = slot[Button]

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = getUi {
    l[VerticalLinearLayout](
      w[TextView] <~ wire(gettingReadyStateTextView),
      l[HorizontalLinearLayout](
        w[TextView] <~ text("Dressed"),
        w[CheckBox] <~ wire(dressedCheckBox)
      ),
      l[HorizontalLinearLayout](
        w[TextView] <~ text("Showered"),
        w[CheckBox] <~ wire(showeredCheckBox)
      ),
      w[Button] <~ wire(dressingButton),
      w[Button] <~ wire(showeringButton)
    )
  }

  def updateData(data: WrappedFSMData): Unit = {
    import GettingReadyFSM._
    data.stateModel.data match {
      case grData: GettingReadyFSM.GettingReadyData =>
        val state = data.stateModel.state
        gettingReadyStateTextView.err(UserInterfaceNotReady) setText state.toString

        val checkBoxes: List[CheckBox] = List(dressedCheckBox, showeredCheckBox).sequenceU.err(UserInterfaceNotReady)
        val checks = List(grData.dressed, grData.showered)
        (checkBoxes zip checks) foreach { case (cb, b) => cb setChecked b}

        val buttons: List[Button] = List(dressingButton, showeringButton).sequenceU.err(UserInterfaceNotReady)
        val defaultLabels: List[Label] = List(
          R.string.start_getting_dressed,
          R.string.start_showering
        )
        buttons match {
          case btns @ List(dressingBtn, showeringBtn) =>
            val button2DefaultLabel = (btns zip defaultLabels).toMap
            val actualButton2Label = state match {
              case GettingDressed => button2DefaultLabel.updated(dressingBtn, R.string.finished_getting_dressed)
              case Showering => button2DefaultLabel.updated(showeringBtn, R.string.stop_showering)
              case _ => button2DefaultLabel
            }
            actualButton2Label foreach { case (btn, label) => btn setText label }
            val dressingMsg = if (actualButton2Label(dressingBtn) == R.string.start_getting_dressed)
              StartGettingDressed else FinishedGettingDressed
            dressingBtn.setOnClickListener(new OnClickListener {
              def onClick(v: View): Unit = reactor ! dressingMsg
            })

            val showeringMsg = if (actualButton2Label(showeringBtn) == R.string.start_showering)
              StartShowering else StopShowering
            showeringBtn.setOnClickListener(new OnClickListener {
              def onClick(v: View): Unit = reactor ! showeringMsg
            })
          case _ => new Exception("Programming Error")
        }
      case _ => new Exception(WrongStateModelType)
    }
  }

  def createReactor: ActorRef = actorSystem.actorOf(GettingReadyReactor.props(this, this.getClass))
}

object GettingReadyReactor {
  def props(owner: GettingReadyFragment, clazz: ThereClazz): Props = Props(new GettingReadyReactor(owner, clazz, GettingReadyFSM.Name))
}

class GettingReadyReactor(owner: GettingReadyFragment, clazz: ThereClazz, fsmName: String) extends
  AbstractReactor(owner, clazz, fsmName) with ReactorOps {
  def receive = baseReceive
}