package com.optrak.androidExperiment

import akka.actor.{ActorRef, Props}
import android.os.Bundle
import android.view.View.OnClickListener
import android.view.{View, ViewGroup, LayoutInflater}
import android.widget.{EditText, Button, TextView}
import com.optrak.androidExperiment.AndroidThere.{ReactorOps, ThereActivity, ThereFragment}
import com.optrak.experiment.Controller.ThereClazz
import com.optrak.experiment.StudyingFSM.ChooseBook
import com.optrak.experiment.{AbstractReactor, StudyingFSM, WrappedFSMData}

import macroid._
import macroid.FullDsl._
import macroid.contrib.Layouts.{HorizontalLinearLayout, VerticalLinearLayout}

import scalaz.std.option.optionSyntax._

/**
 * Created by oscarvarto on 2014/07/22.
 */
class StudyingActivity extends ThereActivity {
  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    val view = l[VerticalLinearLayout](
      f[StudyingFragment].framed(Id.Studying, Tag.Studying)
    )
    setContentView(getUi(view))
  }
}

class StudyingFragment extends ThereFragment {
  import StudyingFSM._
  val name: String = StudyingFSM.Name

  var studyingStateTextView = slot[TextView]
  var currentBookTextView = slot[TextView]
  var placeTextView = slot[TextView]

  var chooseBookEditText = slot[EditText]
  var chooseBookButton = slot[Button]

  var readingButton = slot[Button]
  var setTasksButton = slot[Button]
  var stopStudyingButton = slot[Button]

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = getUi {
    l[VerticalLinearLayout](
      w[TextView] <~ wire(studyingStateTextView),
      w[TextView] <~ wire(currentBookTextView),
      w[TextView] <~ wire(placeTextView),
      l[HorizontalLinearLayout](
        w[EditText] <~ wire(chooseBookEditText),
        w[Button] <~ wire(chooseBookButton)
          <~ text(R.string.choose_book)
          <~ On.click {
               val book: String = chooseBookEditText.err(UserInterfaceNotReady).getText.toString
               if (book.nonEmpty) Ui(reactor ! ChooseBook(book)) else Ui(())
             }
      ),
      w[Button] <~ wire(readingButton),
      w[Button] <~ wire(setTasksButton)
        <~ text(R.string.set_tasks)
        <~ On.click { Ui(reactor ! SetTasks) },
      w[Button] <~ wire(stopStudyingButton)
        <~ text(R.string.stop_studying)
        <~ On.click { Ui(reactor ! StopStudying) }
    )
  }

  def updateData(data: WrappedFSMData): Unit = {
    data.stateModel.data match {
      case studyingData: StudyingData =>
        val state = data.stateModel.state
        studyingStateTextView.err(UserInterfaceNotReady) setText state.toString
        // update currentBookTextView and placeTextView
        for {
          book <- studyingData.currentBook
          currentBookTV <- currentBookTextView
          placeTV <- placeTextView
        } yield {
          currentBookTV setText book
          placeTV setText studyingData.place.toString
          ()
        }
        val (readingLabel, readingMsg) = if (state == Reading)
          (R.string.stop_reading, StopReading)
        else
          (R.string.start_reading, StartReading)
        val readingBtn = readingButton.err(UserInterfaceNotReady)
        readingBtn setText readingLabel
        readingBtn.setOnClickListener(new OnClickListener {
          def onClick(v: View): Unit = reactor ! readingMsg
        })
      case _ => new Exception(WrongStateModelType)
    }
  }

  // TODO: name for Reactor as an actorOf() parameter?
  def createReactor: ActorRef = actorSystem.actorOf(StudyingReactor.props(this, this.getClass))
}

object StudyingReactor {
  def props(owner: StudyingFragment, clazz: ThereClazz): Props = Props(new StudyingReactor(owner, clazz, StudyingFSM.Name))
}

class StudyingReactor(owner: StudyingFragment, clazz: ThereClazz, fsmName: String)
  extends AbstractReactor(owner, clazz, fsmName) with ReactorOps {
  def receive: Receive = baseReceive
}