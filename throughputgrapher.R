args<-commandArgs(TRUE)

library('getopt') 

spec <- matrix(c(
        'zkdata'     , 'z', 1, "character", "zookeeper and official data input file csv (required)",
        'output'    , 'o', 1, "character", "output filename (required)",
        'help'   , 'h', 0, "logical",   "this help"
),ncol=5,byrow=T)

opt = getopt(spec);

if ( !is.null(opt$help) || is.null(opt$zkdata) || is.null(opt$output) ) {
    cat(paste(getopt(spec, usage=T),"\n"));
    q(status=1);
}

#setup output
pdf(opt$output)


mydata <- read.csv(opt$zkdata)

x <- mydata$Time

y1 <- mydata$CPU.z
y2 <- mydata$CPU.o

y3 <- mydata$OPS.s.z
y4 <- mydata$OPS.s.o

# Expand right side of clipping rect to make room for the legend
#par(xpd=T, mar=par()$mar+c(0,0,0,6))
par(oma=c(0,1,0,1), xpd=FALSE)

plot(x=x, y=y1, ylim=c(0.4,1.1), 
	col='blue',
	type='l',
	xlab='Time (seconds)', ylab='CPU utilization (percentile)',
	lty=3,
	#xaxt='n',
	yaxt='n', lwd=1.5,
	# xlim=c(0,50+max(x))
	# bty='U'
	)

par(new=T)
plot(x=x, y=y2, ylim=c(0.4,1.1), 
	col='blue',
	type='l',
	xlab='Time (seconds)', ylab='CPU utilization (percentile)',
	lty=1,
	#xaxt='n',
	yaxt='n', lwd=1.5,
	axes=F,
	# xlim=c(0,50+max(x))
	# bty='U'
	)

# points(x=x, y=y2, col='blue', type='l', lwd=1.0)
axis(2, pretty(c(0, 1.1)), col='blue')

par(new=T)
plot(x=x, y=y3, ylim=c(5000,1.3*max(y3)),
	# xlim=c(0,50+max(x)),
	col='red', type='l', lwd=1.5, lty=3,
	xaxt='n', axes=F, ylab='')

#points(x=x, y=y3, col='red', type='l', lwd=1.5)
par(new=T)
plot(x=x, y=y4, ylim=c(5000,1.3*max(y3)),
	# xlim=c(0,50+max(x)),
	col='red', type='l', lwd=0.75,
	xaxt='n', axes=F, ylab='')

axis(4, pretty(c(0, 1.1*max(y3))), col='red')


par(xpd=TRUE) # disable clipping
legend(
	# x=410, y=12500, 
	"bottomright", inset=c(0.04,0.04),
	legend=c('CPU (zookeeper)', 'CPU (official)', 
		'Throughput (zookeeper)', 'Throughput (official)'), 
	col=c(rep('blue',2), rep('red', 2)), lty=c(3,1,3,1), lwd=c(1.5, 1.5, 1.5, 0.75))

par(xpd=FALSE) # clipping

# abline(v=95, col='gray60')
