#include "coremark.h"
#include <stdarg.h>
#if VALIDATION_RUN
volatile ee_s32 seed1_volatile = 0x3415, seed2_volatile = 0x3415;
#else
volatile ee_s32 seed1_volatile = 0, seed2_volatile = 0;
#endif
volatile ee_s32 seed3_volatile = 0x66;
volatile ee_s32 seed4_volatile = ITERATIONS, seed5_volatile = 0;
ee_u32 default_num_contexts = 1;
static CORE_TICKS begin, end;
static CORE_TICKS ticks(void) {
    volatile ee_u32 *t = (volatile ee_u32 *)0x60000000;
    ee_u32 hi, lo, again;
    do { hi=t[1]; lo=t[0]; again=t[1]; } while (hi != again);
    return ((CORE_TICKS)hi << 32) | lo;
}
void start_time(void) { begin = ticks(); }
void stop_time(void) { end = ticks(); }
CORE_TICKS get_time(void) { return end - begin; }
secs_ret time_in_secs(CORE_TICKS t) { return t / FREQ_HZ; }
static void putch(char c) { *(volatile ee_u8 *)0x30000000 = c; }
static void number(unsigned long n, unsigned base, int width, char pad) {
    char buf[32]; int i=0;
    do { buf[i++]="0123456789abcdef"[n % base]; n/=base; } while(n);
    while(width-- > i) putch(pad);
    while(i) putch(buf[--i]);
}
/* Only the integer/string formats used by upstream CoreMark (HAS_FLOAT=0). */
int ee_printf(const char *fmt, ...) {
    va_list ap; va_start(ap, fmt);
    while (*fmt) {
        if (*fmt != '%') { putch(*fmt++); continue; }
        ++fmt; char pad=' '; int width=0, lng=0;
        if (*fmt=='0') { pad='0'; ++fmt; }
        while (*fmt>='0' && *fmt<='9') width=width*10+(*fmt++-'0');
        if (*fmt=='l') { lng=1; ++fmt; }
        char c=*fmt++;
        if(c=='s') { const char *s=va_arg(ap,const char *); while(*s) putch(*s++); }
        else if(c=='d') { long n=lng?va_arg(ap,long):va_arg(ap,int); if(n<0) putch('-'); number(n<0?0UL-(unsigned long)n:(unsigned long)n,10,width,pad); }
        else if(c=='u' || c=='x') number(lng?va_arg(ap,unsigned long):va_arg(ap,unsigned int),c=='x'?16:10,width,pad);
        else if(c=='c') putch(va_arg(ap,int));
        else if(c=='%') putch('%');
    }
    va_end(ap); return 0;
}
void portable_init(core_portable *p, int *argc, char *argv[]) {
    (void)argc; (void)argv; p->portable_id=1;
    ee_printf("PaSoC CoreMark clock Hz: %u\n", (unsigned)FREQ_HZ);
}
void portable_fini(core_portable *p) {
    p->portable_id=0;
    ee_printf("PASOC_CYCLES_HI=%u PASOC_CYCLES_LO=%u\n", (ee_u32)(get_time()>>32),(ee_u32)get_time());
    /* Drain FIFO, then allow the final serial frame to finish. */
    while (*(volatile ee_u32 *)0x30000000) {}
    CORE_TICKS t=ticks(); while(ticks()-t < FREQ_HZ / BAUD_RATE * 12) {}
}
