package com.miguenduval.mimicdj2.server;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.WireFormat;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Privacy-safe protobuf field-number/wire-type summary. Never emits values. */
public final class ProtobufWireTagSummary {
    private ProtobufWireTagSummary() {}

    public static String summarize(byte[] payload) {
        CodedInputStream input = CodedInputStream.newInstance(payload);
        List<String> fields = new ArrayList<>();
        try {
            int tag;
            while ((tag = input.readTag()) != 0) {
                int fieldNumber = WireFormat.getTagFieldNumber(tag);
                int wireType = WireFormat.getTagWireType(tag);
                if (fieldNumber <= 0) return "INVALID_TAG@" + input.getTotalBytesRead();
                fields.add(fieldNumber + "/wire" + wireType);
                if (!input.skipField(tag)) break;
            }
            return fields.toString();
        } catch (IOException error) {
            return "INVALID@" + input.getTotalBytesRead();
        }
    }
}
