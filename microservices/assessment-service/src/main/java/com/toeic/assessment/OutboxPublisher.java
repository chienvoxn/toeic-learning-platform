package com.toeic.assessment;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component public class OutboxPublisher {
 private static final Logger log=LoggerFactory.getLogger(OutboxPublisher.class);
 private final OutboxRepo outbox;private final RabbitTemplate rabbit;
 OutboxPublisher(OutboxRepo outbox,RabbitTemplate rabbit){this.outbox=outbox;this.rabbit=rabbit;}
 @Scheduled(fixedDelay=3000)
 @Transactional
 public void forward(){for(var e:outbox.findTop20ByPublishedFalseOrderByOccurredAtAsc())try{
  rabbit.convertAndSend("toeic.events","attempt.graded", "{\"eventId\":\""+e.eventId+"\",\"userId\":\""+e.actorId+"\",\"attemptId\":\""+e.aggregateId+"\",\"type\":\"AttemptGraded\"}");
  e.published=true;outbox.save(e);
 }catch(Exception ex){log.warn("Event broker unavailable; retry next poll: {}",ex.getClass().getSimpleName());break;}}
}
