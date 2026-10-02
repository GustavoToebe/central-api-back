package br.com.central.api.integracao;
import jakarta.persistence.*;
import java.util.UUID;
import java.time.*;

@Entity @Table(name="consumo_historico")
public class ConsumoHistorico {
 @Id @GeneratedValue(strategy=GenerationType.UUID) public UUID id;
  @Column(name="contratacao_id",nullable=false) public UUID escopo;
 @Column(nullable=false) public LocalDate dia;
 @Column(name="consultado_em",nullable=false) public Instant consultadoEm;
 @Column(nullable=false,columnDefinition="text") public String dados;
}
