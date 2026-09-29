package br.leds.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.leds.comum.aplicacao.ConsultasConcursos;
import br.leds.comum.aplicacao.ServicoConsultas;
import br.leds.comum.dominio.CandidatoEncontrado;
import br.leds.comum.dominio.ConcursoEncontrado;
import br.leds.comum.erro.ErroInfraestrutura;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Testa só a camada HTTP: o serviço é real, a porta de dados é um dublê (sem banco, sem Spring de dados). */
@WebMvcTest(ConsultasController.class)
@Import({TratadorErros.class, ConsultasControllerTest.Dubles.class})
class ConsultasControllerTest {

    static final ConsultasConcursos QUEBRADO_QUANDO_CPF_9 = new ConsultasConcursos() {
        @Override
        public List<ConcursoEncontrado> concursosPorCpf(String cpf) {
            if (cpf.startsWith("9")) {
                throw new ErroInfraestrutura("jdbc:postgresql://interno:5432 recusou a conexão");
            }
            return List.of(new ConcursoEncontrado("DER", "4/2017", 19750532858L));
        }

        @Override
        public List<CandidatoEncontrado> candidatosPorConcurso(long codigo) {
            return List.of(new CandidatoEncontrado("Ana Sophia Araújo", LocalDate.of(2004, 12, 18), "43815092698"));
        }
    };

    @TestConfiguration
    static class Dubles {
        @Bean
        ServicoConsultas servicoConsultas() {
            return new ServicoConsultas(QUEBRADO_QUANDO_CPF_9);
        }
    }

    @Autowired
    MockMvc mvc;

    @Test
    void concursosDoCandidatoAceitaCpfComPontuacao() throws Exception {
        mvc.perform(get("/api/candidatos/438.150.926-98/concursos"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].orgao").value("DER"))
                .andExpect(jsonPath("$[0].edital").value("4/2017"))
                .andExpect(jsonPath("$[0].codigo").value(19750532858L));
    }

    @Test
    void candidatosDoConcursoTemDataIsoECpfFormatado() throws Exception {
        mvc.perform(get("/api/concursos/19750532858/candidatos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nome").value("Ana Sophia Araújo"))
                .andExpect(jsonPath("$[0].dataNascimento").value("2004-12-18"))
                .andExpect(jsonPath("$[0].cpf").value("438.150.926-98"));
    }

    @Test
    void cpfInvalidoRetorna400ComProblemDetail() throws Exception {
        mvc.perform(get("/api/candidatos/123/concursos"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Entrada inválida"))
                .andExpect(jsonPath("$.detail").value("CPF inválido (esperado 11 dígitos): '123'"));
    }

    @Test
    void codigoNaoNumericoRetorna400() throws Exception {
        mvc.perform(get("/api/concursos/abc/candidatos"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Código de concurso deve ser numérico: 'abc'"));
    }

    @Test
    void falhaDeInfraestruturaRetorna503SemVazarDetalhes() throws Exception {
        mvc.perform(get("/api/candidatos/99999999999/concursos"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("Serviço de dados temporariamente indisponível."))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("jdbc"))));
    }
}
