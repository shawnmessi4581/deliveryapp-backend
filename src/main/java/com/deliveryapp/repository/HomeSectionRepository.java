package com.deliveryapp.repository;

import com.deliveryapp.entity.HomeSection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HomeSectionRepository extends JpaRepository<HomeSection, Long> {

    /** Admin list: every section, in home order. */
    List<HomeSection> findAllByOrderByDisplayOrderAscHomeSectionIdAsc();

    /** Customer home: the sections that are on, in order. */
    List<HomeSection> findByIsActiveTrueOrderByDisplayOrderAscHomeSectionIdAsc();
}
