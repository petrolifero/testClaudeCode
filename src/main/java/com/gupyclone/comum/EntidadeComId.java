package com.gupyclone.comum;

import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;

/**
 * Identidade comum a todas as entidades. equals/hashCode usam a chave primaria
 * quando ela existe e caem na identidade de objeto enquanto a entidade e nova,
 * que e o comportamento seguro para entidades gerenciadas pelo Hibernate.
 */
@MappedSuperclass
public abstract class EntidadeComId {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    public Long getId() {
        return id;
    }

    @Override
    public final boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        if (!(outro instanceof EntidadeComId aquele)) {
            return false;
        }
        if (!getClass().equals(aquele.getClass())) {
            return false;
        }
        return id != null && id.equals(aquele.id);
    }

    @Override
    public final int hashCode() {
        return id == null ? System.identityHashCode(this) : id.hashCode();
    }
}
