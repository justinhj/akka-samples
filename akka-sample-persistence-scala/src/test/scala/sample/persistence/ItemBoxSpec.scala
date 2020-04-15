package sample.persistence

import java.util.UUID

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.scalatest.wordspec.AnyWordSpecLike

class ItemBoxSpec extends ScalaTestWithActorTestKit(s"""
      akka.persistence.journal.plugin = "akka.persistence.journal.inmem"
      akka.persistence.snapshot-store.plugin = "akka.persistence.snapshot-store.local"
      akka.persistence.snapshot-store.local.dir = "target/snapshot-${UUID.randomUUID().toString}"
    """) with AnyWordSpecLike {

  private var counter = 0
  def newBoxId(): String = {
    counter += 1
    s"box-$counter"
  }

  "The Item Box" should {

    "add item" in {
      val box = testKit.spawn(ItemBox(newBoxId()))
      val probe = testKit.createTestProbe[ItemBox.Confirmation]
      box ! ItemBox.AddItem("test item 1", ItemBox.Item("Black Socks", 5), probe.ref)
      probe.expectMessage(ItemBox.ItemAccepted(5))
    }

    "reject no room for item" in {
      val box = testKit.spawn(ItemBox(newBoxId()))
      val probe = testKit.createTestProbe[ItemBox.Confirmation]
      box ! ItemBox.AddItem("test item 2", ItemBox.Item("Giant Black Socks", 12), probe.ref)
      probe.expectMessage(ItemBox.ItemCannotFit(10))
    }

    "add multiple items until failure" in {
      val box = testKit.spawn(ItemBox(newBoxId()))
      val probe = testKit.createTestProbe[ItemBox.Confirmation]

      (1 to 11).foreach {
        n =>
          box ! ItemBox.AddItem(s"test item s", ItemBox.Item("Single Black Socks", 1), probe.ref)
          if(n < 11)
            probe.expectMessage(ItemBox.ItemAccepted(10 - n))
          else
            probe.expectMessage(ItemBox.ItemCannotFit(0))
      }
    }

    // "keep its state" in {
    //   val cartId = newCartId()
    //   val cart = testKit.spawn(ShoppingCart(cartId))
    //   val probe = testKit.createTestProbe[ShoppingCart.Confirmation]
    //   cart ! ShoppingCart.AddItem("foo", 42, probe.ref)
    //   probe.expectMessage(ShoppingCart.Accepted(ShoppingCart.Summary(Map("foo" -> 42), checkedOut = false)))

    //   testKit.stop(cart)

    //   // start again with same cartId
    //   val restartedCart = testKit.spawn(ShoppingCart(cartId))
    //   val stateProbe = testKit.createTestProbe[ShoppingCart.Summary]
    //   restartedCart ! ShoppingCart.Get(stateProbe.ref)
    //   stateProbe.expectMessage(ShoppingCart.Summary(Map("foo" -> 42), checkedOut = false))
    // }
  }

}
