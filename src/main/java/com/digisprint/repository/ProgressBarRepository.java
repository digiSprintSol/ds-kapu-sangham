package com.digisprint.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.digisprint.bean.ProgressBarReport;

@Repository
public interface ProgressBarRepository extends MongoRepository<ProgressBarReport, String> {

}
