//package com.msvcchat.config.aop;
//
//import com.mongodb.reactivestreams.client.ClientSession;
//import org.aspectj.lang.ProceedingJoinPoint;
//import org.aspectj.lang.annotation.Around;
//import org.aspectj.lang.annotation.Aspect;
//import org.springframework.data.mongodb.ReactiveMongoDatabaseFactory;
//import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
//import org.springframework.stereotype.Component;
//import reactor.core.publisher.Flux;
//import reactor.core.publisher.Mono;
//
//@Aspect
//@Component
//public class ReactiveMongoTransactionAspect {
//    private final ReactiveMongoTemplate mongoTemplate;
//    private final ReactiveMongoDatabaseFactory mongoDatabaseFactory;
//    public ReactiveMongoTransactionAspect(ReactiveMongoTemplate mongoTemplate, ReactiveMongoDatabaseFactory mongoDatabaseFactory) {
//        this.mongoTemplate = mongoTemplate;
//        this.mongoDatabaseFactory = mongoDatabaseFactory;
//    }
//    @Around("@annotation(ReactiveMongoTransactional)")
//    public Object manageTransaction(ProceedingJoinPoint joinPoint) throws Throwable {
//        return Mono.usingWhen(
//                mongoDatabaseFactory.getSession(),
//                session -> {
//                    session.startTransaction();
//                    try {
//                        Object result = joinPoint.proceed();
//                        if (result instanceof Mono) {
//                            return ((Mono<?>) result)
//                                    .doOnSuccess(unused -> session.commitTransaction())
//                                    .doOnError(e -> session.abortTransaction());
//                        } else if (result instanceof Flux) {
//                            return ((Flux<?>) result)
//                                    .doOnComplete(session::commitTransaction)
//                                    .doOnError(e -> session.abortTransaction());
//                        } else {
//                            session.abortTransaction();
//                            throw new UnsupportedOperationException("Reactive transactions only support Mono/Flux return types");
//                        }
//                    } catch (Throwable ex) {
//                        session.abortTransaction();
//                        return Mono.error(ex);
//                    }
//                },
//                ClientSession::close
//        );
//    }
//}