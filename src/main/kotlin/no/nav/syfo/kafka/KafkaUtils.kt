package no.nav.syfo.kafka

import java.util.Properties
import kotlin.reflect.KClass
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.*

interface KafkaConfig {
    val kafkaBootstrapServers: String
}

interface KafkaCredentials {
    val kafkaUsername: String
    val kafkaPassword: String
}

fun loadBaseConfig(env: KafkaConfig, credentials: KafkaCredentials): Properties =
    Properties().also {
        it.load(KafkaConfig::class.java.getResourceAsStream("/kafka_base.properties"))
        it["sasl.jaas.config"] =
            "org.apache.kafka.common.security.plain.PlainLoginModule required " +
                "username=\"${credentials.kafkaUsername}\" password=\"${credentials.kafkaPassword}\";"
        it["bootstrap.servers"] = env.kafkaBootstrapServers
        it["specific.avro.reader"] = true
    }

fun Properties.envOverrides() = apply {
    putAll(
        System.getenv()
            .filter { (key, _) -> key.startsWith("KAFKA_") }
            .map { (key, value) -> key.substring(6).lowercase().replace("_", ".") to value }
            .toMap()
    )
}

fun Properties.toConsumerConfig(
    groupId: String,
    valueDeserializer: KClass<out Deserializer<out Any>>,
    keyDeserializer: KClass<out Deserializer<out Any>> = StringDeserializer::class
): Properties =
    Properties().also {
        it.putAll(this)
        it[ConsumerConfig.GROUP_ID_CONFIG] = groupId
        it[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = keyDeserializer.java
        it[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = valueDeserializer.java
        it[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = "1"
    }
