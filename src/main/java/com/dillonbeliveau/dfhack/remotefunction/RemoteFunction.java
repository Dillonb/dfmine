package com.dillonbeliveau.dfhack.remotefunction;

import com.dfhack.protobuf.dfproto.CoreProtocol;
import com.google.protobuf.ByteString;
import com.google.protobuf.GeneratedMessageLite;
import com.google.protobuf.Parser;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.charset.Charset;

/**
 * Created by dillon on 7/16/16.
 */
public class RemoteFunction<Input extends GeneratedMessageLite, Output extends GeneratedMessageLite> {
    private final int MAX_MESSAGE_SIZE = 64 * 1048576;
    private CoreProtocol.CoreBindReply bindReply = null;
    private Socket socket;
    Parser<Output> outputParser;

    private static final Logger log = LogManager.getLogger(RemoteFunction.class);

    private final short RPC_REPLY_RESULT = -1;
    private final short RPC_REPLY_FAIL = -1;
    private final short RPC_REPLY_TEXT = -3;
    private final short RPC_REQUEST_QUIT = -4;

    String byteBufferToString(byte[] buffer) {
        char[] temp = new char[buffer.length];
        ByteBuffer byteBuffer = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN);
        CharBuffer charBuffer = Charset.forName("ASCII").decode(byteBuffer);
        return charBuffer.toString();
    }

    ByteString stringToByteString(String string) {
        return ByteString.copyFrom(string.getBytes(Charset.forName("ASCII")));
    }

    public RemoteFunction(String inputPlugin, Class<Input> inputClass,
                          String outputPlugin, Class<Output> outputClass, Parser<Output> outputParser,
                          String method, Socket socket) throws IOException {
        this(inputPlugin, inputClass,
                outputPlugin, outputClass, outputParser,
                "", method, socket);
    }
    public RemoteFunction(String inputPlugin, Class<Input> inputClass,
                          String outputPlugin, Class<Output> outputClass, Parser<Output> outputParser,
                          String plugin, String method, Socket socket) throws IOException {
        this.socket = socket;
        this.outputParser = outputParser;

        log.info("Binding remote method " + plugin + "::" + method + "...");

        CoreProtocol.CoreBindRequest.Builder requestBuilder = CoreProtocol.CoreBindRequest.newBuilder()
                .setMethod(method)
                .setInputMsg(inputPlugin + "." + inputClass.getSimpleName())
                .setOutputMsg(outputPlugin + "." + outputClass.getSimpleName());

        if (!plugin.equals("")) {
            requestBuilder.setPluginBytes(stringToByteString(plugin));
        }

        byte[] command = requestBuilder.build().toByteArray();

        RPCMessageHeader header = new RPCMessageHeader();
        header.setId((short)0);
        header.setSize(command.length);

        byte[] header_buffer = header.getBytes();

        socket.getOutputStream().write(
                ByteBuffer.allocate(command.length + header_buffer.length).order(ByteOrder.LITTLE_ENDIAN)
                        .put(header_buffer)
                        .put(command)
                        .array());

        InputStream inputStream = socket.getInputStream();
        RPCMessageHeader responseHeader;
        do {
            byte[] replyHeaderBuffer = new byte[8];
            inputStream.read(replyHeaderBuffer);
            responseHeader = new RPCMessageHeader(replyHeaderBuffer);
            switch (responseHeader.getId()) {
                case RPC_REPLY_TEXT:
                    byte[] textBuffer = new byte[responseHeader.getSize()];
                    inputStream.read(textBuffer);
                    String text = byteBufferToString(textBuffer);
                    log.info("Received text reply: " + text);
            }
        } while(responseHeader.getId() != RPC_REPLY_RESULT);

        byte[] reply = new byte[responseHeader.getSize()];
        inputStream.read(reply);

        bindReply = CoreProtocol.CoreBindReply.parseFrom(reply);
    }

    public Output execute(Input arg) throws IOException {
        byte[] input_buffer = arg.toByteArray();
        byte[] header_buffer = new RPCMessageHeader((short) bindReply.getAssignedId(), input_buffer.length).getBytes();

        socket.getOutputStream().write(
                ByteBuffer.allocate(input_buffer.length + header_buffer.length).order(ByteOrder.LITTLE_ENDIAN)
                        .put(header_buffer)
                        .put(input_buffer)
                        .array());

        RPCMessageHeader replyHeader;
        do {
            byte[] replyHeaderBuffer = new byte[8];
            socket.getInputStream().read(replyHeaderBuffer);
            replyHeader = new RPCMessageHeader(replyHeaderBuffer);
        } while (replyHeader.getId() != RPC_REPLY_RESULT);

        byte[] replyBuffer = new byte[replyHeader.getSize()];
        socket.getInputStream().read(replyBuffer);
        return outputParser.parseFrom(replyBuffer);
    }
}
