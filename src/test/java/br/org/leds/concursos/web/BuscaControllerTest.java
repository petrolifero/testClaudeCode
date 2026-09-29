package br.org.leds.concursos.web;

import br.org.leds.concursos.aplicacao.BuscaService;
import br.org.leds.concursos.aplicacao.CandidatoCompativel;
import br.org.leds.concursos.aplicacao.ConcursoCompativel;
import br.org.leds.concursos.dominio.CodigoConcurso;
import br.org.leds.concursos.dominio.Cpf;
import br.org.leds.concursos.dominio.RecursoNaoEncontradoException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BuscaController.class)
class BuscaControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private BuscaService service;

    @Test
    void buscaConcursosPorCpfFormatadoOuNao() throws Exception {
        when(service.concursosParaCandidato(Cpf.of("43815092698")))
                .thenReturn(List.of(new ConcursoCompativel("SEDU", "61828450843", "9/2016")));

        for (String cpf : List.of("438.150.926-98", "43815092698")) {
            mvc.perform(get("/api/candidatos/{cpf}/concursos", cpf))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].orgao").value("SEDU"))
                    .andExpect(jsonPath("$[0].codigo").value("61828450843"))
                    .andExpect(jsonPath("$[0].edital").value("9/2016"));
        }
    }

    @Test
    void buscaCandidatosPorCodigoComDataNoFormatoBrasileiro() throws Exception {
        when(service.candidatosParaConcurso(CodigoConcurso.of("61828450843")))
                .thenReturn(List.of(new CandidatoCompativel("Ana", LocalDate.of(2004, 12, 18), "438.150.926-98")));

        mvc.perform(get("/api/concursos/61828450843/candidatos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nome").value("Ana"))
                .andExpect(jsonPath("$[0].dataNascimento").value("18/12/2004"))
                .andExpect(jsonPath("$[0].cpf").value("438.150.926-98"));
    }

    @Test
    void cpfInvalidoRetorna400ComProblemDetail() throws Exception {
        mvc.perform(get("/api/candidatos/123/concursos"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Parâmetro inválido"))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void codigoInvalidoRetorna400() throws Exception {
        mvc.perform(get("/api/concursos/abc/candidatos")).andExpect(status().isBadRequest());
    }

    @Test
    void naoEncontradoRetorna404() throws Exception {
        when(service.candidatosParaConcurso(any()))
                .thenThrow(new RecursoNaoEncontradoException("Concurso não encontrado para o código 11111111111"));

        mvc.perform(get("/api/concursos/11111111111/candidatos"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Concurso não encontrado para o código 11111111111"));
    }

    @Test
    void falhaDeBancoRetorna503SemVazarDetalhes() throws Exception {
        when(service.concursosParaCandidato(any())).thenThrow(new DataAccessResourceFailureException("conexão recusada"));

        mvc.perform(get("/api/candidatos/43815092698/concursos"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value(
                        "Não foi possível consultar o banco de dados. Tente novamente mais tarde."));
    }
}
