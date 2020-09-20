package io.joyrpc.cluster.distribution;

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

import io.joyrpc.cluster.Candidate;
import io.joyrpc.cluster.Node;
import io.joyrpc.extension.Extensible;
import io.joyrpc.extension.Prototype;
import io.joyrpc.extension.URL;
import io.joyrpc.protocol.message.Invocation;
import io.joyrpc.protocol.message.RequestMessage;

/**
 * 负载均衡接口
 *
 * fixme 负载均衡策略：
 *  - AdaptiveLoadBalance：自适应负载均衡，对节点打分，按分数分发。
 *  - DecoratorLoadBalance：装饰器负载均衡，没看懂
 *  - LocalLoadBalance：本地优先加权随机负载均衡，很简单，local 调用优先，之后随机
 *  - RandomWeightLoadBalance：加权随机负载均衡，按权重分配，然后随机
 *  - RoundRobinLoadBalance：轮询负载均衡
 *  - StickyLoadBalance：粘连算法
 */
//TODO 考虑对集群节点异步化评分和权重分组，负载均衡如果拿到这些分组和评分可以快速计算，这样大规模集群也可以提升性能。例如可以异步化计算权重分组，这样随机权重算法弱化为随机算法。
@Extensible("loadBalance")
public interface LoadBalance extends Prototype {

    /**
     * 选择一个服务实例，不能修改候选者的服务列表
     *
     * @param candidate 候选服务实例列表
     * @param request   请求对象
     * @return Node 被选中的服务实例
     */
    Node select(Candidate candidate, RequestMessage<Invocation> request);

    /**
     * URL
     *
     * @param url
     */
    default void setUrl(final URL url) {
    }

    /**
     * 初始化
     */
    default void setup() {
    }

}
