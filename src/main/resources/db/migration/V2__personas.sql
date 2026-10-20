-- As tres personas do enunciado. Nao existe cadastro nem senha: clicar em uma
-- pessoa no canto superior direito emite um JWT para ela.
insert into recrutador (chave, nome, email)
values ('ana', 'Ana Souza', 'ana.souza@gupyclone.com.br');

insert into candidato (chave, nome, email)
values ('carlos', 'Carlos Pereira', 'carlos.pereira@exemplo.com.br'),
       ('joao', 'Joao Lima', 'joao.lima@exemplo.com.br');
