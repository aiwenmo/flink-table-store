/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.table.store.file.manifest;

import org.apache.flink.core.io.SimpleVersionedSerializer;
import org.apache.flink.core.memory.DataInputDeserializer;
import org.apache.flink.core.memory.DataOutputViewStreamWrapper;
import org.apache.flink.table.store.table.sink.FileCommittable;
import org.apache.flink.table.store.table.sink.FileCommittableSerializer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** {@link SimpleVersionedSerializer} for {@link ManifestCommittable}. */
public class ManifestCommittableSerializer
        implements SimpleVersionedSerializer<ManifestCommittable> {

    private static final int CURRENT_VERSION = 2;

    private final FileCommittableSerializer fileCommittableSerializer;

    public ManifestCommittableSerializer() {
        this.fileCommittableSerializer = new FileCommittableSerializer();
    }

    @Override
    public int getVersion() {
        return CURRENT_VERSION;
    }

    @Override
    public byte[] serialize(ManifestCommittable obj) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataOutputViewStreamWrapper view = new DataOutputViewStreamWrapper(out);
        view.writeUTF(obj.identifier());
        serializeOffsets(view, obj.logOffsets());
        view.writeInt(fileCommittableSerializer.getVersion());
        fileCommittableSerializer.serializeList(obj.fileCommittables(), view);
        return out.toByteArray();
    }

    private void serializeOffsets(DataOutputViewStreamWrapper view, Map<Integer, Long> offsets)
            throws IOException {
        view.writeInt(offsets.size());
        for (Map.Entry<Integer, Long> entry : offsets.entrySet()) {
            view.writeInt(entry.getKey());
            view.writeLong(entry.getValue());
        }
    }

    @Override
    public ManifestCommittable deserialize(int version, byte[] serialized) throws IOException {
        if (version != CURRENT_VERSION) {
            throw new UnsupportedOperationException(
                    "Expecting ManifestCommittable version to be "
                            + CURRENT_VERSION
                            + ", but found "
                            + version
                            + ".\nManifestCommittable is not a compatible data structure. "
                            + "Please restart the job afresh (do not recover from savepoint).");
        }

        DataInputDeserializer view = new DataInputDeserializer(serialized);
        String identifier = view.readUTF();
        Map<Integer, Long> offsets = deserializeOffsets(view);
        int fileCommittableSerializerVersion = view.readInt();
        List<FileCommittable> fileCommittables =
                fileCommittableSerializer.deserializeList(fileCommittableSerializerVersion, view);
        return new ManifestCommittable(identifier, offsets, fileCommittables);
    }

    private Map<Integer, Long> deserializeOffsets(DataInputDeserializer view) throws IOException {
        int size = view.readInt();
        Map<Integer, Long> offsets = new HashMap<>(size);
        for (int i = 0; i < size; i++) {
            offsets.put(view.readInt(), view.readLong());
        }
        return offsets;
    }
}
