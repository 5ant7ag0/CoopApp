package com.cooperativa.core.repository;

import com.cooperativa.core.model.CierreAnual;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface CierreAnualRepository extends JpaRepository<CierreAnual, Integer> {

    List<CierreAnual> findByEmpresaIdOrderByAnioFiscalDesc(Integer empresaId);

    boolean existsByAnioFiscalAndEmpresaId(Integer anioFiscal, Integer empresaId);
}
