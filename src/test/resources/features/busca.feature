# language: pt
Funcionalidade: Buscar concursos e candidatos compatíveis
  Um candidato se encaixa num concurso quando ao menos uma de suas profissões
  está na lista de vagas do concurso.

  Contexto:
    # Exemplo do enunciado do desafio (CPFs ajustados para terem dígitos verificadores válidos)
    Dado os candidatos cadastrados:
      | Nome          | Data de Nascimento | CPF            | Profissões                               |
      | Lindsey Craft | 19/05/1976         | 182.845.084-78 | [carpinteiro]                            |
      | Jackie Dawson | 14/08/1970         | 311.667.973-84 | [marceneiro, assistente administrativo]  |
      | Cory Mendoza  | 11/02/1957         | 565.512.353-36 | [carpinteiro, marceneiro]                |
    E os concursos cadastrados:
      | Órgão | Edital  | Código do Concurso | Lista de vagas                                                  |
      | SEDU  | 9/2016  | 61828450843        | [analista de sistemas, marceneiro]                              |
      | SEJUS | 15/2017 | 61828450843        | [carpinteiro,professor de matemática,assistente administrativo] |
      | SEJUS | 17/2017 | 95655123539        | [professor de matemática]                                       |

  Esquema do Cenário: Listar concursos compatíveis com o CPF do candidato
    Quando eu buscar os concursos do candidato de CPF "<cpf>"
    Então devo receber os concursos:
      | orgao | codigo      | edital  |
      | SEJUS | 61828450843 | 15/2017 |

    Exemplos:
      | cpf            |
      | 182.845.084-78 |
      | 18284508478    |

  Cenário: Candidato com mais de uma profissão encontra concursos de órgãos diferentes
    Quando eu buscar os concursos do candidato de CPF "311.667.973-84"
    Então devo receber os concursos:
      | orgao | codigo      | edital  |
      | SEDU  | 61828450843 | 9/2016  |
      | SEJUS | 61828450843 | 15/2017 |

  Cenário: Listar candidatos compatíveis com o código do concurso
    Quando eu buscar os candidatos do concurso de código "61828450843"
    Então devo receber os candidatos:
      | nome          | dataNascimento | cpf            |
      | Cory Mendoza  | 11/02/1957     | 565.512.353-36 |
      | Jackie Dawson | 14/08/1970     | 311.667.973-84 |
      | Lindsey Craft | 19/05/1976     | 182.845.084-78 |

  Cenário: Concurso sem nenhum candidato compatível
    Quando eu buscar os candidatos do concurso de código "95655123539"
    Então devo receber uma lista vazia

  Cenário: CPF não cadastrado
    Quando eu buscar os concursos do candidato de CPF "529.982.247-25"
    Então devo receber o erro 404 com a mensagem "Candidato não encontrado"

  Cenário: Código de concurso não cadastrado
    Quando eu buscar os candidatos do concurso de código "12345678901"
    Então devo receber o erro 404 com a mensagem "Concurso não encontrado"

  Cenário: CPF inválido
    Quando eu buscar os concursos do candidato de CPF "182.845.084-34"
    Então devo receber o erro 400 com a mensagem "dígitos verificadores inválidos"
