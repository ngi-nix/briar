package org.briarproject.clients;

import org.briarproject.api.FormatException;
import org.briarproject.api.clients.ClientHelper;
import org.briarproject.api.clients.MessageQueueManager.IncomingQueueMessageHook;
import org.briarproject.api.clients.QueueMessage;
import org.briarproject.api.data.BdfDictionary;
import org.briarproject.api.data.BdfList;
import org.briarproject.api.data.MetadataParser;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.Metadata;
import org.briarproject.api.db.Transaction;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.ValidationManager.IncomingMessageHook;
import org.briarproject.api.system.Clock;

import static org.briarproject.api.clients.QueueMessage.QUEUE_MESSAGE_HEADER_LENGTH;
import static org.briarproject.api.sync.SyncConstants.MESSAGE_HEADER_LENGTH;

public abstract class BdfIncomingMessageHook implements IncomingMessageHook,
		IncomingQueueMessageHook {

	protected final ClientHelper clientHelper;
	protected final MetadataParser metadataParser;

	protected BdfIncomingMessageHook(ClientHelper clientHelper,
			MetadataParser metadataParser, Clock clock) {
		this.clientHelper = clientHelper;
		this.metadataParser = metadataParser;
	}

	protected abstract void incomingMessage(Transaction txn, Message m,
			BdfList body, BdfDictionary meta) throws DbException,
			FormatException;

	@Override
	public void incomingMessage(Transaction txn, Message m, Metadata meta)
			throws DbException {
		incomingMessage(txn, m, meta, MESSAGE_HEADER_LENGTH);
	}

	@Override
	public void incomingMessage(Transaction txn, QueueMessage q, Metadata meta)
			throws DbException {
		incomingMessage(txn, q, meta, QUEUE_MESSAGE_HEADER_LENGTH);
	}

	private void incomingMessage(Transaction txn, Message m, Metadata meta,
			int headerLength) throws DbException {
		try {
			byte[] raw = m.getRaw();
			BdfList body = clientHelper.toList(raw, headerLength,
					raw.length - headerLength);
			BdfDictionary metaDictionary = metadataParser.parse(meta);
			incomingMessage(txn, m, body, metaDictionary);
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}
}