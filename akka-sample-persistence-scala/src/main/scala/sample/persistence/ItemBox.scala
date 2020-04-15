package sample.persistence

// this box will be able to accept objects as long as is not full. So a maxCapacity should be included it's state.
// expected behavior:
// should be possible addItem, such as Item(description: String, size: Int)
// should not be possible addItem, if
// maxCapacity is already surpassed or the object to add surpasses it.
// after adding an item it should get back info about how much it still can hold

import akka.actor.typed.Behavior
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.Effect
import akka.persistence.typed.scaladsl.EventSourcedBehavior
import akka.actor.typed.ActorRef

object ItemBox {
  sealed trait Command

  case class Item(description: String, size: Int)

  val maxObjects = 10

  object State {
    def empty = State(maxObjects, List.empty[Item])
  }

  final case class State(capacity: Int, items: List[Item]) {
    def utilized = items.map(_.size).sum
    def remainingRoom = capacity - utilized
  }

  final case class AddItem(item: Item, replyTo: ActorRef[Confirmation]) extends Command

  trait Confirmation

  final case class ItemAccepted(remainingRoom: Int) extends Confirmation
  final case class ItemCannotFit(capacity: Int) extends Confirmation

  sealed trait Event extends CborSerializable {
    def boxId: String
  }
  final case class ItemAdded(boxId: String, item: Item) extends Event

  def itemFits(state: State, item: Item): Boolean = {
    state.capacity >= (state.utilized + item.size)
  }


  def getRemainingRoom(state: State): Int = state.capacity - state.utilized

  def apply(boxId: String): Behavior[Command] = {
    EventSourcedBehavior[Command, Event, State](PersistenceId("Box", boxId),
      State.empty,
      (state, command) => {
        command match {
          case AddItem(item, replyTo) => {
            if(!itemFits(state, item)) {
              replyTo ! ItemCannotFit(getRemainingRoom(state))
              Effect.none
            } else {
              Effect
                .persist(ItemAdded(boxId, item))
                .thenRun(updatedBox => {
                  replyTo ! ItemAccepted(getRemainingRoom(updatedBox))
                })
            }
          }
        }
      },
      (state, event) => {
          event match {
            case ItemAdded(boxId, item) =>
              state.copy(items = state.items :+ item)
          }
        })
  }
}
