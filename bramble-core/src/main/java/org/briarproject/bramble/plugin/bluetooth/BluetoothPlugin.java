package org.briarproject.bramble.plugin.bluetooth;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.keyagreement.KeyAgreementConnection;
import org.briarproject.bramble.api.keyagreement.KeyAgreementListener;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.bramble.api.plugin.Backoff;
import org.briarproject.bramble.api.plugin.PluginException;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.plugin.duplex.DuplexPlugin;
import org.briarproject.bramble.api.plugin.duplex.DuplexPluginCallback;
import org.briarproject.bramble.api.plugin.duplex.DuplexTransportConnection;
import org.briarproject.bramble.api.properties.TransportProperties;
import org.briarproject.bramble.util.StringUtils;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.bramble.api.keyagreement.KeyAgreementConstants.TRANSPORT_ID_BLUETOOTH;
import static org.briarproject.bramble.api.plugin.BluetoothConstants.ID;
import static org.briarproject.bramble.api.plugin.BluetoothConstants.PREF_BT_ENABLE;
import static org.briarproject.bramble.api.plugin.BluetoothConstants.PROP_ADDRESS;
import static org.briarproject.bramble.api.plugin.BluetoothConstants.PROP_UUID;
import static org.briarproject.bramble.api.plugin.BluetoothConstants.UUID_BYTES;
import static org.briarproject.bramble.util.PrivacyUtils.scrubMacAddress;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
abstract class BluetoothPlugin<SS> implements DuplexPlugin {

	private static final Logger LOG =
			Logger.getLogger(BluetoothPlugin.class.getName());

	final Executor ioExecutor;

	private final SecureRandom secureRandom;
	private final Backoff backoff;
	private final DuplexPluginCallback callback;
	private final int maxLatency;
	private final AtomicBoolean used = new AtomicBoolean(false);

	private volatile boolean running = false;
	private volatile SS socket = null;

	abstract void initialiseAdapter() throws IOException;

	abstract boolean isAdapterEnabled();

	abstract void enableAdapter();

	@Nullable
	abstract String getBluetoothAddress();

	abstract SS openServerSocket(String uuid) throws IOException;

	abstract void tryToClose(@Nullable SS ss);

	abstract DuplexTransportConnection acceptConnection(SS ss)
			throws IOException;

	abstract boolean isValidAddress(String address);

	abstract DuplexTransportConnection connectTo(String address, String uuid)
			throws IOException;

	BluetoothPlugin(Executor ioExecutor, SecureRandom secureRandom,
			Backoff backoff, DuplexPluginCallback callback, int maxLatency) {
		this.ioExecutor = ioExecutor;
		this.secureRandom = secureRandom;
		this.backoff = backoff;
		this.callback = callback;
		this.maxLatency = maxLatency;
	}

	void onAdapterEnabled() {
		LOG.info("Bluetooth enabled");
		bind();
	}

	void onAdapterDisabled() {
		LOG.info("Bluetooth disabled");
		tryToClose(socket);
		callback.transportDisabled();
	}

	@Override
	public TransportId getId() {
		return ID;
	}

	@Override
	public int getMaxLatency() {
		return maxLatency;
	}

	@Override
	public int getMaxIdleTime() {
		// Bluetooth detects dead connections so we don't need keepalives
		return Integer.MAX_VALUE;
	}

	@Override
	public void start() throws PluginException {
		if (used.getAndSet(true)) throw new IllegalStateException();
		try {
			initialiseAdapter();
		} catch (IOException e) {
			throw new PluginException(e);
		}
		running = true;
		// If Bluetooth is enabled, bind a socket
		if (isAdapterEnabled()) {
			bind();
		} else {
			// Enable Bluetooth if settings allow
			if (callback.getSettings().getBoolean(PREF_BT_ENABLE, false)) {
				enableAdapter();
			} else {
				LOG.info("Not enabling Bluetooth");
			}
		}
	}

	private void bind() {
		ioExecutor.execute(() -> {
			if (!isRunning()) return;
			String address = getBluetoothAddress();
			if (LOG.isLoggable(INFO))
				LOG.info("Local address " + scrubMacAddress(address));
			if (!StringUtils.isNullOrEmpty(address)) {
				// Advertise our Bluetooth address to contacts
				TransportProperties p = new TransportProperties();
				p.put(PROP_ADDRESS, address);
				callback.mergeLocalProperties(p);
			}
			// Bind a server socket to accept connections from contacts
			SS ss;
			try {
				ss = openServerSocket(getUuid());
			} catch (IOException e) {
				if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
				return;
			}
			if (!isRunning()) {
				tryToClose(ss);
				return;
			}
			socket = ss;
			backoff.reset();
			callback.transportEnabled();
			acceptContactConnections();
		});
	}

	private String getUuid() {
		String uuid = callback.getLocalProperties().get(PROP_UUID);
		if (uuid == null) {
			byte[] random = new byte[UUID_BYTES];
			secureRandom.nextBytes(random);
			uuid = UUID.nameUUIDFromBytes(random).toString();
			TransportProperties p = new TransportProperties();
			p.put(PROP_UUID, uuid);
			callback.mergeLocalProperties(p);
		}
		return uuid;
	}

	private void acceptContactConnections() {
		while (true) {
			DuplexTransportConnection conn;
			try {
				conn = acceptConnection(socket);
			} catch (IOException e) {
				// This is expected when the socket is closed
				if (LOG.isLoggable(INFO)) LOG.info(e.toString());
				return;
			}
			backoff.reset();
			callback.incomingConnectionCreated(conn);
			if (!running) return;
		}
	}

	@Override
	public void stop() {
		running = false;
		tryToClose(socket);
		callback.transportDisabled();
	}

	@Override
	public boolean isRunning() {
		return running;
	}

	@Override
	public boolean shouldPoll() {
		return true;
	}

	@Override
	public int getPollingInterval() {
		return backoff.getPollingInterval();
	}

	@Override
	public void poll(Collection<ContactId> connected) {
		if (!isRunning()) return;
		backoff.increment();
		// Try to connect to known devices in parallel
		Map<ContactId, TransportProperties> remote =
				callback.getRemoteProperties();
		for (Entry<ContactId, TransportProperties> e : remote.entrySet()) {
			ContactId c = e.getKey();
			if (connected.contains(c)) continue;
			String address = e.getValue().get(PROP_ADDRESS);
			if (StringUtils.isNullOrEmpty(address)) continue;
			String uuid = e.getValue().get(PROP_UUID);
			if (StringUtils.isNullOrEmpty(uuid)) continue;
			ioExecutor.execute(() -> {
				if (!isRunning()) return;
				DuplexTransportConnection conn = connect(address, uuid);
				if (conn != null) {
					backoff.reset();
					callback.outgoingConnectionCreated(c, conn);
				}
			});
		}
	}

	@Nullable
	private DuplexTransportConnection connect(String address, String uuid) {
		// Validate the address
		if (!isValidAddress(address)) {
			if (LOG.isLoggable(WARNING))
				// Not scrubbing here to be able to figure out the problem
				LOG.warning("Invalid address " + address);
			return null;
		}
		// Validate the UUID
		try {
			//noinspection ResultOfMethodCallIgnored
			UUID.fromString(uuid);
		} catch (IllegalArgumentException e) {
			if (LOG.isLoggable(WARNING)) LOG.warning("Invalid UUID " + uuid);
			return null;
		}
		if (LOG.isLoggable(INFO))
			LOG.info("Connecting to " + scrubMacAddress(address));
		try {
			DuplexTransportConnection conn = connectTo(address, uuid);
			if (LOG.isLoggable(INFO))
				LOG.info("Connected to " + scrubMacAddress(address));
			return conn;
		} catch (IOException e) {
			if (LOG.isLoggable(INFO))
				LOG.info("Could not connect to " + scrubMacAddress(address));
			return null;
		}
	}

	@Override
	public DuplexTransportConnection createConnection(ContactId c) {
		if (!isRunning()) return null;
		TransportProperties p = callback.getRemoteProperties(c);
		String address = p.get(PROP_ADDRESS);
		if (StringUtils.isNullOrEmpty(address)) return null;
		String uuid = p.get(PROP_UUID);
		if (StringUtils.isNullOrEmpty(uuid)) return null;
		return connect(address, uuid);
	}

	@Override
	public boolean supportsKeyAgreement() {
		return true;
	}

	@Override
	public KeyAgreementListener createKeyAgreementListener(byte[] commitment) {
		if (!isRunning()) return null;
		// There's no point listening if we can't discover our own address
		String address = getBluetoothAddress();
		if (address == null) return null;
		// No truncation necessary because COMMIT_LENGTH = 16
		String uuid = UUID.nameUUIDFromBytes(commitment).toString();
		if (LOG.isLoggable(INFO)) LOG.info("Key agreement UUID " + uuid);
		// Bind a server socket for receiving key agreement connections
		SS ss;
		try {
			ss = openServerSocket(uuid);
		} catch (IOException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			return null;
		}
		if (!isRunning()) {
			tryToClose(ss);
			return null;
		}
		BdfList descriptor = new BdfList();
		descriptor.add(TRANSPORT_ID_BLUETOOTH);
		descriptor.add(StringUtils.macToBytes(address));
		return new BluetoothKeyAgreementListener(descriptor, ss);
	}

	@Override
	public DuplexTransportConnection createKeyAgreementConnection(
			byte[] commitment, BdfList descriptor, long timeout) {
		if (!isRunning()) return null;
		String address;
		try {
			address = parseAddress(descriptor);
		} catch (FormatException e) {
			LOG.info("Invalid address in key agreement descriptor");
			return null;
		}
		// No truncation necessary because COMMIT_LENGTH = 16
		String uuid = UUID.nameUUIDFromBytes(commitment).toString();
		if (LOG.isLoggable(INFO))
			LOG.info("Connecting to key agreement UUID " + uuid);
		return connect(address, uuid);
	}

	private String parseAddress(BdfList descriptor) throws FormatException {
		byte[] mac = descriptor.getRaw(1);
		if (mac.length != 6) throw new FormatException();
		return StringUtils.macToString(mac);
	}

	private class BluetoothKeyAgreementListener extends KeyAgreementListener {

		private final SS ss;

		private BluetoothKeyAgreementListener(BdfList descriptor, SS ss) {
			super(descriptor);
			this.ss = ss;
		}

		@Override
		public Callable<KeyAgreementConnection> listen() {
			return () -> {
				DuplexTransportConnection conn = acceptConnection(ss);
				if (LOG.isLoggable(INFO))
					LOG.info(ID.getString() + ": Incoming connection");
				return new KeyAgreementConnection(conn, ID);
			};
		}

		@Override
		public void close() {
			tryToClose(ss);
		}
	}
}