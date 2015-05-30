/**
 * 
 */
package io.sipstack.actor;

import io.hektor.core.Actor;
import io.hektor.core.ActorContext;
import io.netty.util.Timeout;
import io.netty.util.Timer;
import io.netty.util.TimerTask;
import io.pkts.packet.sip.SipMessage;
import io.pkts.packet.sip.SipRequest;
import io.pkts.packet.sip.SipResponse;
import io.sipstack.config.SipConfiguration;
import io.sipstack.event.Event;
import io.sipstack.event.SipMsgEvent;
import io.sipstack.netty.codec.sip.Connection;
import io.sipstack.netty.codec.sip.ConnectionId;
import io.sipstack.netty.codec.sip.event.SipMessageEvent;
import io.sipstack.netty.codec.sip.Transport;
import io.sipstack.netty.codec.sip.config.TransactionLayerConfiguration;
import io.sipstack.transaction.TransactionId;
import io.sipstack.transaction.impl.TransactionSupervisor;
import org.junit.After;
import org.junit.Before;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author jonas@jonasborjesson.com
 */
public class SipTestBase {

    protected ConnectionId defaultConnectionId;

    protected SipConfiguration sipConfig = new SipConfiguration();

    protected SipRequest invite;
    protected SipResponse ringing;
    protected SipResponse twoHundredToInvite;
    protected SipRequest bye;
    protected SipResponse twoHundredToBye;

    private final String inviteStr;
    private final String ringingStr;
    private final String twoHundredToInviteStr;
    private final String ackStr;
    private final String byeStr;
    private final String twoHundredByeStr;

    /**
     * The transaction supervisor used for mosts tests within this tests and
     * especially in the case of the transaction tests.
     */
    protected TransactionSupervisor supervisor;

    /**
     * The fake timer used for checking whether tasks where scheduled for later execution.
     */
    protected MockTimer timer;

    /**
     * An event proxy that is inserted before the supervisor.
     */
    protected EventProxy first;

    /**
     * An event proxy that is inserted after the supervisor.
     */
    protected EventProxy last;

    /**
     * Most scenarios for the INVITE server transaction start off with
     * an invite and if so, just use this one.
     */
    protected SipMsgEvent defaultInviteEvent;

    /**
     * The transaction id for the default invite event.
     */
    protected TransactionId defaultInviteTransactionId;

    /**
     * Default 180 Ringing that is in the same transaction as the {@link #defaultInviteEvent}.
     */
    protected SipMsgEvent default180RingingEvent;

    /**
     * Default 200 OK that is in the same transaction as the {@link #defaultInviteEvent}.
     */
    protected SipMsgEvent default200OKEvent;

    /**
     * Default BYE request commonly used by the non-invite transaction tests.
     */
    protected SipMsgEvent defaultByeEvent;

    /**
     * The transaction Id for the default bye event.
     */
    protected TransactionId defaultByeTransactionId;

    /**
     * The list of all non-invite requests. Typically, ALL tests that we execute will
     * go through all requests there are in SIPs various specifications.
     */
    protected List<SipMsgEvent> nonInviteRequests;

    public SipTestBase() throws Exception {
        this.defaultConnectionId = createConnectionId(Transport.udp, "10.36.10.10", 5060, "192.168.0.100", 5060);

        StringBuilder sb = new StringBuilder();
        sb.append("INVITE sip:service@127.0.0.1:5060 SIP/2.0\r\n");
        sb.append("Via: SIP/2.0/UDP 127.0.1.1:5061;branch=z9hG4bK-25980-1-0\r\n");
        sb.append("From: sipp <sip:sipp@127.0.1.1:5061>;tag=25980SIPpTag001\r\n");
        sb.append("To: sut <sip:service@127.0.0.1:5060>\r\n");
        sb.append("Call-ID: 1-25980@127.0.1.1\r\n");
        sb.append("CSeq: 1 INVITE\r\n");
        sb.append("Contact: sip:sipp@127.0.1.1:5061\r\n");
        sb.append("Max-Forwards: 70\r\n");
        sb.append("Subject: Performance Test\r\n");
        sb.append("Content-Type: application/sdp\r\n");
        sb.append("Content-Length:   129\r\n");
        sb.append("\r\n");
        sb.append("v=0\r\n");
        sb.append("o=user1 53655765 2353687637 IN IP4 127.0.1.1\r\n");
        sb.append("s=-\r\n");
        sb.append("c=IN IP4 127.0.1.1\r\n");
        sb.append("t=0 0\r\n");
        sb.append("m=audio 6000 RTP/AVP 0\r\n");
        sb.append("a=rtpmap:0 PCMU/8000\r\n");
        this.inviteStr = sb.toString();

        sb = new StringBuilder();
        sb.append("SIP/2.0 180 OK\r\n");
        sb.append("From: sipp <sip:sipp@127.0.1.1:5061>;tag=25980SIPpTag001\r\n");
        sb.append("To: sut <sip:service@127.0.0.1:5060>\r\n");
        sb.append("Call-ID: 1-25980@127.0.1.1\r\n");
        sb.append("CSeq: 1 INVITE\r\n");
        sb.append("Via: SIP/2.0/UDP 127.0.1.1:5061;branch=z9hG4bK-25980-1-0\r\n");
        sb.append("Max-Forwards: 70\r\n");
        this.ringingStr = sb.toString();

        sb = new StringBuilder();
        sb.append("SIP/2.0 200 OK\r\n");
        sb.append("From: sipp <sip:sipp@127.0.1.1:5061>;tag=25980SIPpTag001\r\n");
        sb.append("To: sut <sip:service@127.0.0.1:5060>\r\n");
        sb.append("Call-ID: 1-25980@127.0.1.1\r\n");
        sb.append("CSeq: 1 INVITE\r\n");
        sb.append("Via: SIP/2.0/UDP 127.0.1.1:5061;branch=z9hG4bK-25980-1-0\r\n");
        sb.append("Max-Forwards: 70\r\n");
        this.twoHundredToInviteStr = sb.toString();


        sb = new StringBuilder();
        sb.append("ACK sip:service@127.0.0.1:5060 SIP/2.0\r\n");
        sb.append("Via: SIP/2.0/UDP 127.0.1.1:5061;branch=z9hG4bK-25980-1-5\r\n");
        sb.append("From: sipp <sip:sipp@127.0.1.1:5061>;tag=25980SIPpTag001\r\n");
        sb.append("To: sut <sip:service@127.0.0.1:5060>\r\n");
        sb.append("Call-ID: 1-25980@127.0.1.1\r\n");
        sb.append("CSeq: 1 ACK\r\n");
        sb.append("Contact: sip:sipp@127.0.1.1:5061\r\n");
        sb.append("Max-Forwards: 70\r\n");
        sb.append("Subject: Performance Test\r\n");
        sb.append("Content-Length: 0\r\n");
        this.ackStr = sb.toString();

        sb = new StringBuilder();
        sb.append("BYE sip:service@127.0.0.1:5060 SIP/2.0\r\n");
        sb.append("Via: SIP/2.0/UDP 127.0.1.1:5061;branch=z9hG4bK-25980-1-7\r\n");
        sb.append("From: sipp <sip:sipp@127.0.1.1:5061>;tag=25980SIPpTag001\r\n");
        sb.append("To: sut <sip:service@127.0.0.1:5060>\r\n");
        sb.append("Call-ID: 1-25980@127.0.1.1\r\n");
        sb.append("CSeq: 2 BYE\r\n");
        sb.append("Contact: sip:sipp@127.0.1.1:5061\r\n");
        sb.append("Max-Forwards: 70\r\n");
        sb.append("Subject: Performance Test\r\n");
        sb.append("Content-Length: 0\r\n");
        this.byeStr = sb.toString();

        sb = new StringBuilder();
        sb.append("SIP/2.0 200 OK\r\n");
        sb.append("From: sipp <sip:sipp@127.0.1.1:5061>;tag=25980SIPpTag001\r\n");
        sb.append("To: sut <sip:service@127.0.0.1:5060>\r\n");
        sb.append("Call-ID: 1-25980@127.0.1.1\r\n");
        sb.append("CSeq: 2 BYE\r\n");
        sb.append("Via: SIP/2.0/UDP 127.0.1.1:5061;branch=z9hG4bK-25980-1-7\r\n");
        sb.append("Max-Forwards: 70\r\n");
        this.twoHundredByeStr = sb.toString();
    }

    /**
     * @throws java.lang.Exception
     */
    @Before
    public void setUp() throws Exception {
        this.invite = SipMessage.frame(this.inviteStr).toRequest();
        this.ringing = SipMessage.frame(this.ringingStr).toResponse();
        this.twoHundredToInvite = SipMessage.frame(this.twoHundredToInviteStr).toResponse();
        this.bye = SipMessage.frame(this.byeStr).toRequest();
        this.twoHundredToBye = SipMessage.frame(this.twoHundredByeStr).toResponse();
    }

    /**
     * @throws java.lang.Exception
     */
    @After
    public void tearDown() throws Exception {}

    /**
     * Setup the system with the option to respond with a set of responses. The responses will be
     * created by the {@link EventProxy} that is configured last in the pipeline and is as such
     * acting as the application using the transaction.
     *
     * @param responses
     */
    protected void init(final Integer... responses) {
        init(this.sipConfig.getTransaction(), responses);
    }

    protected void init(List<Actor> chain) {
        defaultInviteEvent = SipMsgEvent.create(invite);
        defaultInviteTransactionId = getTransactionId(defaultInviteEvent);
        default180RingingEvent = SipMsgEvent.create(ringing);
        default200OKEvent = SipMsgEvent.create(twoHundredToInvite);

        defaultByeEvent = SipMsgEvent.create(bye);
        defaultByeTransactionId = getTransactionId(defaultByeEvent);

        nonInviteRequests = Arrays.asList(defaultByeEvent);
    }

    protected void init(final TransactionLayerConfiguration config, final Integer... responses) {
        timer = new MockTimer();

        first = new EventProxy();
        last = new EventProxy(responses);
        supervisor = new TransactionSupervisor(config);
        init(Arrays.asList(first, supervisor, last));
    }

    protected TransactionId getTransactionId(final SipMsgEvent event) {
        return TransactionId.create(event.getSipMessage());
    }

    /**
     * Helper method for creating a new {@link ConnectionId} object.
     */
    public ConnectionId createConnectionId(final Transport transport, final String localIp, final int localPort,
            final String remoteIp, final int remotePort) throws Exception {
        final InetSocketAddress local = new InetSocketAddress(localIp, localPort);
        final InetSocketAddress remote = new InetSocketAddress(remoteIp, remotePort);
        return ConnectionId.create(transport, local, remote);
    }

    public SipMessageEvent mockSipMessageEvent(final SipMessage msg) {
        final SipMessageEvent event = mock(SipMessageEvent.class);
        final Connection connection = mockConnection(this.defaultConnectionId);
        // TODO: should be our internal fake clock
        when(event.arrivalTime()).thenReturn(System.currentTimeMillis());
        when(event.connection()).thenReturn(connection);
        when(event.message()).thenReturn(msg);
        return event;
    }

    /**
     * Helper method for creating a mocked {@link Connection} based off of a {@link ConnectionId}.
     * 
     * @param connectionId
     * @return
     */
    public Connection mockConnection(final ConnectionId connectionId) {
        final Connection connection = mock(Connection.class);
        when(connection.id()).thenReturn(connectionId);
        when(connection.getTransport()).thenReturn(connectionId.getProtocol());
        when(connection.getLocalIpAddress()).thenReturn(connectionId.getLocalIpAddress());
        when(connection.getLocalPort()).thenReturn(connectionId.getLocalPort());
        when(connection.getRemoteIpAddress()).thenReturn(connectionId.getRemoteIpAddress());
        when(connection.getRemotePort()).thenReturn(connectionId.getRemotePort());
        return connection;
    }


    /**
     * Simple {@link Timer} that simply just saves all the events so that we can examine them later
     * and also "fire" them off..
     * 
     * @author jonas@jonasborjesson.com
     *
     */
    public static class MockTimer implements Timer {
        public final List<TimerTaskSnapshot> tasks = new ArrayList<TimerTaskSnapshot>();

        @Override
        public Timeout newTimeout(final TimerTask task, final long delay, final TimeUnit unit) {
            final Timeout timeout = mock(Timeout.class);
            this.tasks.add(new TimerTaskSnapshot(task, delay, unit, timeout));
            return timeout;
        }

        @Override
        public Set<Timeout> stop() {
            // TODO Auto-generated method stub
            return null;
        }

    }

    /**
     * Simple class just to keep all the parameters together when it comes to the timer task that
     * was scheduled on a {@link Timer}.
     * 
     */
    public static class TimerTaskSnapshot {
        public final TimerTask task;
        public final long delay;
        public final TimeUnit unit;
        public final Timeout timeout;

        private TimerTaskSnapshot(final TimerTask task, final long delay, final TimeUnit unit, final Timeout timeout) {
            this.task = task;
            this.delay = delay;
            this.unit = unit;
            this.timeout = timeout;
        }
    }

    /**
     * Simple {@link Actor} that simply just forwards the message in the same direction it came and
     * saves all the events it has seen.
     * 
     * @author jonas
     *
     */
    public static class EventProxy implements Actor {

        public final List<Event> requestEvents = new ArrayList<Event>();
        public final List<Event> responseEvents = new ArrayList<Event>();

        List<Integer> responses;

        /**
         * The last context we got.
         */
        private ActorContext lastCtx;

        private Event lastEvent;

        public EventProxy(final Integer... responses) {
            if (responses != null && responses.length > 0) {
                this.responses = Arrays.asList(responses);
            } else {
                this.responses = Collections.emptyList();
            }
        }

        public void reset() {
            this.requestEvents.clear();
            this.responseEvents.clear();
        }

        @Override
        public void onReceive(final Object msg) {
            final Event event = (Event)msg;
            if (event.isSipMsgEvent()) {
                final SipMessage sipMsg = event.toSipMsgEvent().getSipMessage();
                if (sipMsg.isRequest()) {
                    this.requestEvents.add(event);
                    for (final Integer responseStatus : this.responses) {
                        final SipRequest request = sipMsg.toRequest();
                        final SipResponse response = request.createResponse(responseStatus);
                        final SipMsgEvent responseEvent = SipMsgEvent.create(response);
                        sender().tell(responseEvent, self());
                    }
                } else {
                    this.responseEvents.add(event);
                }

                // TODO: should go to out configured next actor
                // ctx.forward(event);
            }

        }
    }



}
