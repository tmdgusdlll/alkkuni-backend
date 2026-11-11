package com.alkkuni.core.brand.repository

import com.alkkuni.core.brand.entity.Brand
import com.alkkuni.core.brand.entity.BrandAlias
import org.springframework.data.jpa.repository.JpaRepository

interface BrandRepository : JpaRepository<Brand, Long>

interface BrandAliasRepository : JpaRepository<BrandAlias, String>
