package io.joyrpc.protocol.dubbo.serialization.hessian2;

/*-
 * #%L
 * joyrpc
 * %%
 * Copyright (C) 2019 joyrpc.io
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import io.joyrpc.codec.serialization.*;
import io.joyrpc.codec.serialization.hessian2.Hessian2BWLInput;
import io.joyrpc.codec.serialization.hessian2.Hessian2Reader;
import io.joyrpc.codec.serialization.hessian2.Hessian2Serialization;
import io.joyrpc.codec.serialization.hessian2.Hessian2Writer;
import io.joyrpc.com.caucho.hessian.io.AutowiredObjectDeserializer;
import io.joyrpc.com.caucho.hessian.io.AutowiredObjectSerializer;
import io.joyrpc.com.caucho.hessian.io.Hessian2Output;
import io.joyrpc.com.caucho.hessian.io.SerializerFactory;
import io.joyrpc.extension.Extension;
import io.joyrpc.permission.SerializerBlackWhiteList;
import io.joyrpc.util.Resource.Definition;

import java.io.InputStream;
import java.io.OutputStream;

@Extension(value = "hessian", provider = "dubbo", order = Serialization.ORDER_DUBBO_HESSIAN)
public class DubboHessian2Serialization extends Hessian2Serialization {

    @Override
    public byte getTypeId() {
        return DUBBO_HESSIAN_ID;
    }

    @Override
    public Serializer getSerializer() {
        return DubboHessian2Serializer.INSTANCE;
    }

    /**
     * Hessian2序列化和反序列化实现
     */
    protected static final class DubboHessian2Serializer extends AbstractSerializer {

        protected static final SerializerBlackWhiteList BLACK_WHITE_LIST = new SerializerBlackWhiteList(
                new Definition[]{
                        new Definition("permission/hessian.blacklist"),
                        new Definition("META-INF/permission/hessian.blacklist", true)});

        protected static final SerializerFactory SERIALIZER_FACTORY = new SerializerFactory(Thread.currentThread().getContextClassLoader());

        protected static final DubboHessian2Serializer INSTANCE = new DubboHessian2Serializer();

        /**
         * 线程缓存，优化性能
         */
        protected static final ThreadLocal<Hessian2Output> HESSIAN_OUTPUT = ThreadLocal.withInitial(() -> {
            Hessian2Output result = new Hessian2Output(null);
            result.setSerializerFactory(SERIALIZER_FACTORY);
            result.setCloseStreamOnClose(true);
            return result;
        });

        /**
         * 线程缓存，优化性能
         */
        protected static final ThreadLocal<Hessian2BWLInput> HESSIAN_INPUT = ThreadLocal.withInitial(() -> {
            Hessian2BWLInput result = new Hessian2BWLInput(BLACK_WHITE_LIST);
            result.setSerializerFactory(SERIALIZER_FACTORY);
            result.setCloseStreamOnClose(true);
            return result;
        });

        static {
            SERIALIZER_FACTORY.setAllowNonSerializable(true);
            DubboHessian2SerializerFactory factory = new DubboHessian2SerializerFactory();
            register(AutowiredObjectSerializer.class, o -> factory.serializers.put(o.getType(), o));
            register(AutowiredObjectDeserializer.class, o -> factory.deserializers.put(o.getType(), o));
            if (factory.deserializers.isEmpty()) {
                factory.deserializers = null;
            }
            SERIALIZER_FACTORY.addFactory(factory);
        }

        @Override
        protected ObjectWriter createWriter(final OutputStream os, final Object object) {
            Hessian2Output output = HESSIAN_OUTPUT.get();
            output.init(os);
            return new Hessian2Writer(output);
        }

        @Override
        protected ObjectReader createReader(final InputStream is, final Class clazz) {
            Hessian2BWLInput input = HESSIAN_INPUT.get();
            input.init(is);
            return new Hessian2Reader(input);
        }
    }
}
