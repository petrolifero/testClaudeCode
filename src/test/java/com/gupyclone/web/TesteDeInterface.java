package com.gupyclone.web;

import com.gupyclone.suporte.TesteDeIntegracao;

/** Base dos testes de interface: cada teste ganha um navegador limpo. */
abstract class TesteDeInterface extends TesteDeIntegracao {

    protected NavegadorDeTeste navegador() {
        return new NavegadorDeTeste(enderecoBase());
    }
}
