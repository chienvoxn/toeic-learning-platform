package com.toeic.assessment;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.amqp.core.*;
@Configuration public class MessagingConfig {
 @Bean TopicExchange toeicEvents(){return new TopicExchange("toeic.events",true,false);}
 @Bean Queue activityQueue(){return QueueBuilder.durable("interaction.attempt-graded").build();}
 @Bean Binding activityBinding(Queue activityQueue,TopicExchange toeicEvents){return BindingBuilder.bind(activityQueue).to(toeicEvents).with("attempt.graded");}
}
