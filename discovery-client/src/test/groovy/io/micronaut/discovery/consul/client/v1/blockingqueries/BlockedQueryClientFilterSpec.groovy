package io.micronaut.discovery.consul.client.v1.blockingqueries


import io.micronaut.http.MutableHttpParameters
import io.micronaut.http.MutableHttpRequest
import spock.lang.Specification

import static io.micronaut.discovery.consul.client.v1.blockingqueries.BlockedQueryClientFilter.PARAMETER_INDEX
import static io.micronaut.discovery.consul.client.v1.blockingqueries.BlockedQueryClientFilter.PARAMETER_WAIT

class BlockedQueryClientFilterSpec extends Specification {

    BlockingQueriesConfiguration watchConfiguration = Mock()
    MutableHttpRequest<?> request = Mock()
    MutableHttpParameters parameters = Mock()

    BlockedQueryClientFilter blockedQueryClientFilter = new BlockedQueryClientFilter(watchConfiguration)

    void "test that index parameter is not added when index is not present"() {
        given:
        1 * request.getParameters() >> parameters
        1 * parameters.contains(PARAMETER_INDEX) >> false

        when:
        blockedQueryClientFilter.filterRequest(request)

        then:
        0 * parameters.add(_, _)
    }

    void "test that index parameter is added when index is present"() {
        given:
        1 * request.getParameters() >> parameters
        1 * parameters.contains(PARAMETER_INDEX) >> true
        1 * watchConfiguration.getMaxWaitDuration() >> "666s"

        when:
        blockedQueryClientFilter.filterRequest(request)

        then:
        1 * parameters.add(PARAMETER_WAIT, "666s")
    }
}
