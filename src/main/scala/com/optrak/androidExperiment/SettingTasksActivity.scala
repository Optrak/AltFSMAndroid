package com.optrak.androidExperiment

import akka.actor.{Actor, ActorRef, Props}
import android.app.ListFragment
import android.content.Context
import android.os.Bundle
import android.view.ViewGroup.LayoutParams._
import android.view.{Gravity, LayoutInflater, View, ViewGroup}
import android.widget.CompoundButton.OnCheckedChangeListener
import android.widget._
import com.optrak.androidExperiment.AndroidThere.{ReactorOps, ThereActivity, ThereFragment}
import com.optrak.experiment.AbstractFragment.{ClosedFromOutside, CreatedThereActivity}
import com.optrak.experiment.ActivityActor.SetData
import com.optrak.experiment.Controller._
import com.optrak.experiment.SettingTasksFSM._
import com.optrak.experiment._
import com.optrak.opkfsm.OpkFSM.Msg
import macroid.FullDsl._
import macroid._
import macroid.contrib.Layouts.VerticalLinearLayout
import macroid.contrib.TextTweaks
import org.joda.time.DateTime
import grizzled.slf4j.Logging

import scala.collection.JavaConversions._
import scala.collection.mutable.ArrayBuffer

/**
 * Created by oscarvarto on 2014/07/22.
 */
class SettingTasksActivity extends ThereActivity {
  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    val view = l[VerticalLinearLayout](
      f[TaskListFragment].framed(Id.SettingTasks, Tag.SettingTasks),
      f[ButtonsFragment].framed(Id.Buttons, Tag.Buttons)
    )
    setContentView(getUi(view))
  }
}

case class MTask(id: Int, creationDate: DateTime) {
  var title: String = "Task Title"
  var description: String = "Put a description here"
  var done: Boolean = false
}

object Conversions {
  implicit def mutable2immutableTask(mt: MTask): Task =
    Task(mt.id, mt.title, mt.description, mt.done)

  implicit def immutable2mutableTask(t: Task): MTask = {
    val mt = MTask(t.id, t.creationDate)
    mt.title = t.title
    mt.description = t.description
    mt.done = t.done
    mt
  }
}

class TaskListFragment extends ListFragment with ThereFragment with IdGeneration {
  import com.optrak.androidExperiment.Conversions._

  val name: String = SettingTasksFSM.Name

  var mTasks = ArrayBuffer.empty[MTask]

  override def onCreate(savedInstanceState: Bundle): Unit = {
    debug("TaskListFragment created")
    super.onCreate(savedInstanceState)
    getActivity.setTitle(R.string.tasks_title)
    setListAdapter(new TaskAdapter())
  }

  def updateData(data: WrappedFSMData): Unit = {
    data.stateModel.data match {
      case tData: SettingTasksData =>
        val newTasks: Vector[MTask] = tData.tasks map { t => t: MTask}
        mTasks = ArrayBuffer(newTasks: _*)
        debug(s"TaskListFragment.updateData() called, resulting in new $mTasks")
        showNewTaskList()
      case _ => new Exception(WrongStateModelType)
    }
  }

  def showNewTaskList(): Unit = {
    val adapter = getListAdapter.asInstanceOf[TaskAdapter]
    adapter.clear()
    adapter.addAll(mTasks)
    getListAdapter.asInstanceOf[TaskAdapter].notifyDataSetChanged()
  }

  def createReactor: ActorRef =
    actorSystem.actorOf(SettingTasksReactor.props(this, this.getClass), SettingTasksReactor.Name)

  private class TaskAdapter(context: Context, resource: Int, objects: JList[MTask]) extends
  ArrayAdapter[MTask](context, resource, objects) {
    def this() = this(getActivity, 0, mTasks)

    override def getView(position: Int, convertView: View, parent: ViewGroup): View = {
      val task: MTask = getItem(position)
      val view = l[RelativeLayout](
        w[CheckBox] <~ id(Id.done) <~
          layoutParams[RelativeLayout](WRAP_CONTENT, WRAP_CONTENT) <~
          doneCheckBoxTweak(task, position) <~ enable(true) <~ padding(4 dp),
        w[TextView] <~ text(task.title) <~
          TextTweaks.bold <~
          layoutParams[RelativeLayout](MATCH_PARENT, WRAP_CONTENT) <~
          taskTitleTweak
      )
      getUi(view)
    }

    def doneCheckBoxTweak(mTask: MTask, position: Int) = Tweak[CheckBox] { checkbox =>

      checkbox.setGravity(Gravity.CENTER)
      checkbox.setFocusable(true)

      checkbox.setChecked(mTask.done) // <--- very important

      val rlp = checkbox.getLayoutParams.asInstanceOf[RelativeLayout.LayoutParams]
      rlp.addRule(RelativeLayout.ALIGN_PARENT_RIGHT)
      checkbox.setLayoutParams(rlp)

      checkbox.setOnCheckedChangeListener {
        new OnCheckedChangeListener {
          def onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean): Unit = {
            mTask.done = isChecked
            reactor ! TaskModified(mTask, position) // <---
          }
        }
      }
    }

    def taskTitleTweak = Tweak[TextView] { textview =>
      textview.setFocusable(false)
      val rlp = textview.getLayoutParams.asInstanceOf[RelativeLayout.LayoutParams]
      rlp.addRule(RelativeLayout.LEFT_OF, Id.done)
      textview.setLayoutParams(rlp)
    }
  }

}

object SettingTasksReactor {
  val Name = "SettingTasksReactor"

  def props(owner: TaskListFragment, clazz: ThereClazz): Props =
    Props(new SettingTasksReactor(owner, clazz, SettingTasksFSM.Name))
}

class SettingTasksReactor(owner: TaskListFragment, clazz: ThereClazz, fsmName: String)
  extends AbstractReactor(owner, clazz, fsmName) with ReactorOps {


  @scala.throws[Exception](classOf[Exception]) override
  def preStart(): Unit = {
    debug("SettingTasksReactor created")
    super.preStart()
  }

  def receive: Receive = {
    case AbstractFragment.CreateThereActivity(clazz, eventStamp) => {
      doOnUIThread { () => owner.spawnActivity(clazz) }
      sender ! CreatedThereActivity(clazz, eventStamp)
    }
    case AbstractFragment.CloseThereActivity =>
      doOnUIThread {() =>  owner.stop() }
      context.stop(self)
    case s: SetData =>
      doSetData(s.data)
      //buttonsReactor ! s
    case msg: Msg => {
      logger.debug(s"$self gets $msg from $sender, sending on to $activityActor")
      activityActor.foreach( x=> x ! msg )
    }
    case ClosedFromOutside => {
      context.stop(self)
    }
  }
}

class ButtonsFragment extends ThereFragment {
  val name: String = SettingTasksReactor.Name
  var stopSettingTasksButton = slot[Button]
  var appendTaskButton = slot[Button]

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = getUi {
    l[VerticalLinearLayout](
      w[Button] <~ wire(stopSettingTasksButton)
        <~ text(getString(R.string.stop_setting_tasks))
        <~ On.click { Ui(reactor ! StopSettingTasks) }
      ,
      w[Button] <~ wire(appendTaskButton)
        <~ text(getString(R.string.append_task))
        <~ On.click { Ui(reactor ! AppendTask(Task(0, "Blah"))) }
    )
  }

  def updateData(data: WrappedFSMData): Unit = ()

  def createReactor: ActorRef = actorSystem.actorOf(ButtonsReactor.props(this, s"/user/$name"), ButtonsReactor.Name)
}

object ButtonsReactor {
  val Name = "ButtonsReactor"

  def props(owner: ButtonsFragment, settingTasksReactorSelection: String): Props =
    Props(new ButtonsReactor(owner, settingTasksReactorSelection))
}

class ButtonsReactor(owner: ButtonsFragment, settingTasksReactorSelection: String) extends Actor with Logging {
  val settingTasksReactor = context.actorSelection(settingTasksReactorSelection)

  def receive = {
    case StopSettingTasks =>
      //debug(s"$self received StopSettingTasks")
      settingTasksReactor ! StopSettingTasks // Output message
    case at: AppendTask =>
      //debug(s"$self received $at")
      settingTasksReactor ! at // Output message
    //case sd: SetData => Ui{ owner.updateData(sd.data) }.run // Input message (not necessary for this example project).
    case _ => new Exception("Programming Error")
  }
}