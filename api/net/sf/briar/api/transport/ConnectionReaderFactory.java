package net.sf.briar.api.transport;

import java.io.InputStream;

import net.sf.briar.api.protocol.TransportIndex;

public interface ConnectionReaderFactory {

	/**
	 * Creates a connection reader for a batch-mode connection or the
	 * initiator's side of a stream-mode connection. The secret is erased before
	 * returning.
	 */
	ConnectionReader createConnectionReader(InputStream in, TransportIndex i,
			byte[] encryptedIv, byte[] secret);

	/**
	 * Creates a connection reader for the responder's side of a stream-mode
	 * connection. The secret is erased before returning.
	 */
	ConnectionReader createConnectionReader(InputStream in, TransportIndex i,
			long connection, byte[] secret);
}
