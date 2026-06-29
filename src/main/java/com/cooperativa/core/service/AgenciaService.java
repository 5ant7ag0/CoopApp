package com.cooperativa.core.service;

import com.cooperativa.core.dto.AgenciaDTO;
import com.cooperativa.core.model.Agencia;
import com.cooperativa.core.repository.AgenciaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AgenciaService {

    private final AgenciaRepository agenciaRepository;

    public List<AgenciaDTO> listarAgencias() {
        return agenciaRepository.findAll().stream().map(this::toDTO).collect(Collectors.toList());
    }

    public AgenciaDTO crearAgencia(AgenciaDTO dto) {
        if (agenciaRepository.findByCodigo(dto.getCodigo()).isPresent()) {
            throw new RuntimeException("Ya existe una agencia con el código: " + dto.getCodigo());
        }
        Agencia entity = new Agencia();
        entity.setCodigo(dto.getCodigo());
        entity.setNombre(dto.getNombre());
        entity.setDireccion(dto.getDireccion());
        if (dto.getEstado() != null) entity.setEstado(dto.getEstado());
        
        Agencia saved = agenciaRepository.save(entity);
        return toDTO(saved);
    }

    private AgenciaDTO toDTO(Agencia entity) {
        AgenciaDTO dto = new AgenciaDTO();
        dto.setId(entity.getId());
        dto.setCodigo(entity.getCodigo());
        dto.setNombre(entity.getNombre());
        dto.setDireccion(entity.getDireccion());
        dto.setEstado(entity.getEstado());
        dto.setCreatedAt(entity.getCreatedAt());
        return dto;
    }
}
