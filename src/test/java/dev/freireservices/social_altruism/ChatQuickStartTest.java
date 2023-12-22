package dev.freireservices.social_altruism;

import akka.actor.testkit.typed.javadsl.ActorTestKit;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.ActorRef;
import java.time.Duration;
import org.junit.AfterClass;
import org.junit.Test;

// #definition
public class ChatQuickStartTest {

  static final ActorTestKit testKit = ActorTestKit.create();

  // #definition

  // #test
  @Test
  public void testGreeterActorSendingOfGreeting() {

    TestProbe<ChatRoom.PostMessage> testProbe = testKit.createTestProbe();

    ActorRef<ChatRoom.RoomCommand> chatRoomTest = testKit.spawn(ChatRoom.create(), "chatRoom");
    ActorRef<ChatRoom.SessionEvent> gabblerTest = testKit.spawn(Gabbler.create(), "gabbler");

    chatRoomTest.tell(new ChatRoom.GetSession("ol’ Gabbler", gabblerTest));

    ChatRoom.SessionGranted sessionGranted = new ChatRoom.SessionGranted(testProbe.ref());
    gabblerTest.tell(sessionGranted);

    ChatRoom.PostMessage postMessage = new ChatRoom.PostMessage("Hello World!");
    sessionGranted.handle.tell(postMessage);
    // #test

    // #assert
    testProbe.expectMessage(postMessage);
    // #assert

    testKit.stop(chatRoomTest);
    testKit.stop(gabblerTest);
  }

  @Test
  public void testActorGetsUserDenied() {

    TestProbe<ChatRoom.SessionEvent> testProbe = testKit.createTestProbe();

    ActorRef<ChatRoom.RoomCommand> chatRoomTest = testKit.spawn(ChatRoom.create(), "chatRoom");

    chatRoomTest.tell(new ChatRoom.GetSession("ol’ Gabbler", testProbe.ref()));

    testProbe.expectMessageClass(ChatRoom.SessionGranted.class, Duration.ofSeconds(10));

    chatRoomTest.tell(new ChatRoom.GetSession("ol’ Gabbler", testProbe.ref()));

    ChatRoom.SessionDenied sessionDenied = new ChatRoom.SessionDenied("duplicate session name");

    testProbe.expectMessage(Duration.ofSeconds(10), sessionDenied);

    // #assert
  }

  @AfterClass
  public static void cleanup() {
    testKit.system().terminate();
    testKit.shutdownTestKit();
  }
}
