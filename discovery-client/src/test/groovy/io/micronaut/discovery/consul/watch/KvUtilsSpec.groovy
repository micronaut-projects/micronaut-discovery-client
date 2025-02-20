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
        new KeyValue("key", "value", 0) | null                              | false
        null                            | new KeyValue("key", "value", 0)   | false
        new KeyValue("key", "value", 0) | new KeyValue("key_2", "value", 0) | false
        new KeyValue("key", "value", 0) | new KeyValue("key", "value_2", 0) | false
        new KeyValue("key", "value", 0) | new KeyValue("key", "value", 0)   | true
    }

    void "test comparison between 2 lists of KeyValues"(final List<KeyValue> left, final List<KeyValue> right, final boolean expected) {
        when:
        var actual = KvUtils.areEqual(left, right)

        then:
        actual == expected

        where:
        left                                                                           | right                                                                          | expected
        null                                                                           | null                                                                           | true
        createList(new KeyValue("key", "value", 0))                                    | null                                                                           | false
        null                                                                           | createList(new KeyValue( "key", "value", 0))                                    | false
        createList(new KeyValue("key", "value", 0))                                    | createList(new KeyValue("key_2", "value", 0))                                  | false
        createList(new KeyValue("key", "value", 0), new KeyValue("key_2", "value", 0)) | createList(new KeyValue("key_2", "value", 0))                                  | false
        createList(new KeyValue("key", "value", 0))                                    | createList(new KeyValue("key", "value", 0), new KeyValue("key_2", "value", 0)) | false
        createList(new KeyValue("key", "value", 0))                                    | createList(new KeyValue("key", "value_2", 0))                                  | false
        createList(new KeyValue("key", "value", 0))                                    | createList(new KeyValue("key", "value", 0))                                    | true
        createList(new KeyValue("key", "value", 0), new KeyValue("key_2", "value", 0)) | createList(new KeyValue("key_2", "value", 0), new KeyValue("key", "value", 0)) | true
    }

    private static List<KeyValue> createList(KeyValue... keyValues) {
        var list = new ArrayList<KeyValue>()
        for (final def kv in keyValues) {
            list.add(kv)
        }

        return list
    }
}
