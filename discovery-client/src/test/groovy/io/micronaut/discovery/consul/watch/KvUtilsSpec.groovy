package io.micronaut.discovery.consul.watch


import io.micronaut.discovery.consul.client.v1.KeyValue
import spock.lang.Specification

class KvUtilsSpec extends Specification {

    void "test comparison between 2 KeyValues"(KeyValue left, KeyValue right, boolean expected) {
        when:
        var actual = KvUtils.areEqual(left as KeyValue, right as KeyValue)

        then:
        actual == expected

        where:
        left                            | right                             | expected
        null                            | null                              | true
        new KeyValue(0, "key", "value") | null                              | false
        null                            | new KeyValue(0, "key", "value")   | false
        new KeyValue(0, "key", "value") | new KeyValue(0, "key_2", "value") | false
        new KeyValue(0, "key", "value") | new KeyValue(0, "key", "value_2") | false
        new KeyValue(0, "key", "value") | new KeyValue(0, "key", "value")   | true
    }

    void "test comparison between 2 lists of KeyValues"(final List<KeyValue> left, final List<KeyValue> right, final boolean expected) {
        when:
        var actual = KvUtils.areEqual(left, right)

        then:
        actual == expected

        where:
        left                                                                           | right                                                                          | expected
        null                                                                           | null                                                                           | true
        createList(new KeyValue(0, "key", "value"))                                    | null                                                                           | false
        null                                                                           | createList(new KeyValue(0, "key", "value"))                                    | false
        createList(new KeyValue(0, "key", "value"))                                    | createList(new KeyValue(0, "key_2", "value"))                                  | false
        createList(new KeyValue(0, "key", "value"), new KeyValue(0, "key_2", "value")) | createList(new KeyValue(0, "key_2", "value"))                                  | false
        createList(new KeyValue(0, "key", "value"))                                    | createList(new KeyValue(0, "key", "value"), new KeyValue(0, "key_2", "value")) | false
        createList(new KeyValue(0, "key", "value"))                                    | createList(new KeyValue(0, "key", "value_2"))                                  | false
        createList(new KeyValue(0, "key", "value"))                                    | createList(new KeyValue(0, "key", "value"))                                    | true
        createList(new KeyValue(0, "key", "value"), new KeyValue(0, "key_2", "value")) | createList(new KeyValue(0, "key_2", "value"), new KeyValue(0, "key", "value")) | true
    }

    private static List<KeyValue> createList(KeyValue... keyValues) {
        var list = new ArrayList<KeyValue>()
        for (final def kv in keyValues) {
            list.add(kv)
        }

        return list
    }
}
