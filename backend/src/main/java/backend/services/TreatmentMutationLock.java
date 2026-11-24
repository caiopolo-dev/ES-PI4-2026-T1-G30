// Autor: Samuel Campovilla
package backend.services;
// Mutation refere-se a uma operação que altera o estado de um objeto.
//
// Um monitor é um mecanismo de sincronização associado a um objeto Java,
// utilizado para garantir exclusão mútua entre threads.
//
// Ao utilizar synchronized com o mesmo monitor, garantimos que apenas
// uma thread execute uma das seções críticas protegidas por vez.
//
// Essa abordagem evita condições de corrida entre a geração,
// alteração e cancelamento de doses dentro da mesma JVM.
final class TreatmentMutationLock {
    // static garante que o monitor seja compartilhado.
    // final impede que sua referência seja substituída.
    static final Object MONITOR = new Object();
    private TreatmentMutationLock() {
    }
}

// Utilizamos um monitor compartilhado entre os serviços para impedir,
// por exemplo, que uma thread altere um cronograma enquanto outra
// estiver gerando doses com base nesse mesmo cronograma.
//
// Essa proteção funciona somente entre threads da mesma JVM.