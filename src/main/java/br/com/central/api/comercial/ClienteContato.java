package br.com.central.api.comercial;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "cliente_contato")
public class ClienteContato {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    @Column(nullable = false, length = 200)
    private String nome;

    @Column(length = 200)
    private String email;

    @Column(length = 30)
    private String telefone;

    @Column(nullable = false)
    private boolean principal;

    protected ClienteContato() {
    }

    public ClienteContato(String nome, String email, String telefone, boolean principal) {
        this.nome = nome;
        this.email = email;
        this.telefone = telefone;
        this.principal = principal;
    }

    void setCliente(Cliente cliente) {
        this.cliente = cliente;
    }

    public UUID getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public String getEmail() {
        return email;
    }

    public String getTelefone() {
        return telefone;
    }

    public boolean isPrincipal() {
        return principal;
    }
}
